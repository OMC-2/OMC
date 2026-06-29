package com.omc.product.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.product.domain.entity.Inventory;
import com.omc.product.domain.entity.Product;
import com.omc.product.domain.enums.OutboxEventType;
import com.omc.product.domain.enums.OutboxStatus;
import com.omc.product.domain.repository.InventoryRepository;
import com.omc.product.domain.repository.OutboxEventRepository;
import com.omc.product.domain.repository.ProcessedEventRepository;
import com.omc.product.domain.repository.ProductRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 재고 통합 테스트
 * PostgreSQL, Kafka, Redis TestContainers + WireMock(Drop Service Feign 대체)
 */
class InventoryIntegrationTest extends AbstractIntegrationTest{

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ProductRepository productRepository;
    @Autowired InventoryRepository inventoryRepository;
    @Autowired OutboxEventRepository outboxEventRepository;
    @Autowired ProcessedEventRepository processedEventRepository;
    @Autowired KafkaTemplate<String, String> kafkaTemplate;

    private Product savedProduct;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        processedEventRepository.deleteAll();
        inventoryRepository.deleteAll();
        productRepository.deleteAll();
        wireMock.resetAll();

        savedProduct = Product.create("Switch 2", "설명", 689000L,
                "Nintendo", "게이밍 기기", null);
        productRepository.save(savedProduct);
        Inventory inventory = Inventory.create(savedProduct.getProductId(), 10);
        inventoryRepository.save(inventory);
    }

    @Test
    void 재고_스냅샷_조회_성공() throws Exception {
        mockMvc.perform(get("/internal/v1/products/{productId}/inventories/snapshot",
                        savedProduct.getProductId())
                        .header("X-Gateway-Secret", GW_SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalQuantity").value(10))
                .andExpect(jsonPath("$.data.soldQuantity").value(0));
    }

    @Test
    void 재고_상세_조회_성공_ADMIN() throws Exception {
        mockMvc.perform(get("/api/v1/admin/products/{productId}/inventories",
                        savedProduct.getProductId())
                        .header("X-Gateway-Secret", GW_SECRET)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalQuantity").value(10));
    }

    @Test
    void 진행중_드롭_없으면_재고_수동_수정_성공() throws Exception {
        stubHasActiveDrop(savedProduct.getProductId(), false);

        String request = """
                { "totalQuantity": 20, "reason": "입고 추가 10개" }
                """;

        mockMvc.perform(patch("/api/v1/admin/products/{productId}/inventories",
                        savedProduct.getProductId())
                        .header("X-Gateway-Secret", GW_SECRET)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalQuantity").value(20));

        Inventory updated = inventoryRepository
                .findByProductId(savedProduct.getProductId()).orElseThrow();
        assertThat(updated.getTotalQuantity()).isEqualTo(20);
    }

    @Test
    void 진행중_드롭_있으면_재고_수동_수정_409() throws Exception {
        stubHasActiveDrop(savedProduct.getProductId(), true);

        String request = """
                { "totalQuantity": 20, "reason": "입고 추가" }
                """;

        mockMvc.perform(patch("/api/v1/admin/products/{productId}/inventories",
                        savedProduct.getProductId())
                        .header("X-Gateway-Secret", GW_SECRET)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict());
    }

    @Test
    void payment_completed_수신_후_재고_확정_차감_성공() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String payload = """
            {
                "eventId": "%s",
                "orderId": "%s",
                "productId": "%s",
                "userId": "%s",
                "dropId": "%s",
                "finalAmount": 189000
            }
            """.formatted(
                eventId,
                UUID.randomUUID(),
                savedProduct.getProductId(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        kafkaTemplate.send("payment.completed", eventId, payload);

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {

                    Inventory updated = inventoryRepository
                            .findByProductId(savedProduct.getProductId())
                            .orElseThrow();
                    assertThat(updated.getSoldQuantity()).isEqualTo(1);

                    assertThat(processedEventRepository
                            .existsByEventId(eventId)).isTrue();

                    assertThat(outboxEventRepository.findAll())
                            .anyMatch(outbox ->
                                    outbox.getEventType() == OutboxEventType.STOCK_DEDUCTED
                                    && outbox.getStatus() == OutboxStatus.PUBLISHED);
                });
    }

    @Test
    void payment_completed_재고_부족_시_STOCK_FAILED_발행() throws Exception {
        Inventory inventory = inventoryRepository
                .findByProductId(savedProduct.getProductId()).orElseThrow();
        ReflectionTestUtils.setField(inventory, "soldQuantity", 10);
        inventoryRepository.saveAndFlush(inventory);

        String eventId = UUID.randomUUID().toString();
        String payload = """
            {
                "eventId": "%s",
                "orderId": "%s",
                "productId": "%s",
                "userId": "%s",
                "dropId": "%s",
                "finalAmount": 189000
            }
            """.formatted(
                eventId,
                UUID.randomUUID(),
                savedProduct.getProductId(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        kafkaTemplate.send("payment.completed", eventId, payload);

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    assertThat(outboxEventRepository.findAll())
                            .anyMatch(outbox ->
                                    outbox.getEventType() == OutboxEventType.STOCK_FAILED
                                    && outbox.getStatus() == OutboxStatus.PUBLISHED);

                    Inventory updated = inventoryRepository
                            .findByProductId(savedProduct.getProductId()).orElseThrow();
                    assertThat(updated.getSoldQuantity()).isEqualTo(10);
                });
    }

    @Test
    void payment_completed_중복_수신_시_멱등성_보장() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String payload = """
            {
                "eventId": "%s",
                "orderId": "%s",
                "productId": "%s",
                "userId": "%s",
                "dropId": "%s",
                "finalAmount": 189000
            }
            """.formatted(
                eventId,
                UUID.randomUUID(),
                savedProduct.getProductId(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        kafkaTemplate.send("payment.completed", eventId, payload);
        kafkaTemplate.send("payment.completed", eventId, payload);

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Inventory updated = inventoryRepository
                            .findByProductId(savedProduct.getProductId())
                            .orElseThrow();
                    assertThat(updated.getSoldQuantity()).isEqualTo(1);  // 2가 아닌 1
                });
    }
}