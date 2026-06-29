package com.omc.product.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.product.domain.entity.Inventory;
import com.omc.product.domain.entity.Product;
import com.omc.product.domain.repository.InventoryRepository;
import com.omc.product.domain.repository.OutboxEventRepository;
import com.omc.product.domain.repository.ProcessedEventRepository;
import com.omc.product.domain.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 상품 CRUD 통합 테스트
 * PostgreSQL, Kafka, Redis TestContainers + WireMock(Drop Service Feign 대체)
 */
class ProductCrudIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ProductRepository productRepository;
    @Autowired InventoryRepository inventoryRepository;
    @Autowired OutboxEventRepository outboxEventRepository;
    @Autowired ProcessedEventRepository processedEventRepository;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        processedEventRepository.deleteAll();
        inventoryRepository.deleteAll();
        productRepository.deleteAll();
        resetWireMock();
    }

    @Test
    void 상품_등록_성공_DB_저장_확인() throws Exception {
        String request = """
                {
                    "name": "Switch 2",
                    "description": "설명",
                    "price": 689000,
                    "brand": "Nintendo",
                    "category": "게이밍 기기",
                    "initialQuantity": 10
                }
                """;

        mockMvc.perform(post("/api/v1/admin/products")
                        .header("X-Gateway-Secret", GW_SECRET)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Switch 2"))
                .andExpect(jsonPath("$.data.availableQuantity").value(0));

        assertThat(productRepository.findAll()).hasSize(1);
        assertThat(inventoryRepository.findAll()).hasSize(1);

        Inventory inventory = inventoryRepository.findAll().get(0);
        assertThat(inventory.getTotalQuantity()).isEqualTo(10);
        assertThat(inventory.getSoldQuantity()).isEqualTo(0);
    }

    @Test
    void 상품_목록_조회_성공() throws Exception {
        Product product = Product.create("Switch 2", "설명", 689000L,
                "Nintendo", "게이밍 기기", null);
        productRepository.save(product);
        Inventory inventory = Inventory.create(product.getProductId(), 10);
        inventoryRepository.save(inventory);

        mockMvc.perform(get("/api/v1/products")
                        .header("X-Gateway-Secret", GW_SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("Switch 2"));
    }

    @Test
    void 상품_상세_조회_성공() throws Exception {
        Product product = Product.create("Switch 2", "설명", 689000L,
                "Nintendo", "게이밍 기기", null);
        productRepository.save(product);
        Inventory inventory = Inventory.create(product.getProductId(), 10);
        inventoryRepository.save(inventory);

        mockMvc.perform(get("/api/v1/products/{productId}", product.getProductId())
                        .header("X-Gateway-Secret", GW_SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(product.getProductId().toString()))
                .andExpect(jsonPath("$.data.name").value("Switch 2"));
    }

    @Test
    void 존재하지_않는_상품_조회_시_404() throws Exception {
        mockMvc.perform(get("/api/v1/products/{productId}", UUID.randomUUID())
                        .header("X-Gateway-Secret", GW_SECRET))
                .andExpect(status().isNotFound());
    }

    @Test
    void 진행중_드롭_없으면_상품_수정_성공() throws Exception {
        Product product = Product.create("Switch 2", "설명", 689000L,
                "Nintendo", "게이밍 기기", null);
        productRepository.save(product);
        Inventory inventory = Inventory.create(product.getProductId(), 10);
        inventoryRepository.save(inventory);
        stubHasActiveDrop(product.getProductId(), false);

        String request = """
                { "name": "수정된 상품명", "price": 200000 }
                """;

        mockMvc.perform(patch("/api/v1/admin/products/{productId}", product.getProductId())
                        .header("X-Gateway-Secret", GW_SECRET)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("수정된 상품명"));
    }

    @Test
    void 진행중_드롭_있으면_상품_수정_409() throws Exception {
        Product product = Product.create("Switch 2", "설명", 689000L,
                "Nintendo", "게이밍 기기", null);
        productRepository.save(product);
        Inventory inventory = Inventory.create(product.getProductId(), 10);
        inventoryRepository.save(inventory);
        stubHasActiveDrop(product.getProductId(), true);

        String request = """
                { "name": "수정된 상품명" }
                """;

        mockMvc.perform(patch("/api/v1/admin/products/{productId}", product.getProductId())
                        .header("X-Gateway-Secret", GW_SECRET)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict());
    }

    @Test
    void 진행중_드롭_없으면_상품_삭제_성공_DB_확인() throws Exception {
        Product product = Product.create("Switch 2", "설명", 689000L,
                "Nintendo", "게이밍 기기", null);
        productRepository.save(product);
        Inventory inventory = Inventory.create(product.getProductId(), 10);
        inventoryRepository.save(inventory);
        stubHasActiveDrop(product.getProductId(), false);

        mockMvc.perform(delete("/api/v1/admin/products/{productId}", product.getProductId())
                        .header("X-Gateway-Secret", GW_SECRET)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());

        assertThat(productRepository.findById(product.getProductId())).isEmpty();
    }

    @Test
    void Drop_Service_타임아웃_시_상품_수정_503() throws Exception {
        Product product = Product.create("Switch 2", "설명", 689000L,
                "Nintendo", "게이밍 기기", null);
        productRepository.save(product);
        Inventory inventory = Inventory.create(product.getProductId(), 10);
        inventoryRepository.save(inventory);
        stubDropServiceTimeout();

        String request = """
                { "name": "수정된 상품명" }
                """;

        mockMvc.perform(patch("/api/v1/admin/products/{productId}", product.getProductId())
                        .header("X-Gateway-Secret", GW_SECRET)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("PRODUCT-020"));
    }

    @Test
    void Drop_Service_타임아웃_시_상품_삭제_503() throws Exception {
        Product product = Product.create("Switch 2", "설명", 689000L,
                "Nintendo", "게이밍 기기", null);
        productRepository.save(product);
        Inventory inventory = Inventory.create(product.getProductId(), 10);
        inventoryRepository.save(inventory);
        stubDropServiceTimeout();

        mockMvc.perform(delete("/api/v1/admin/products/{productId}", product.getProductId())
                        .header("X-Gateway-Secret", GW_SECRET)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("PRODUCT-020"));
    }

    @Test
    void Drop_Service_타임아웃_시_재고_수동_수정_503() throws Exception {
        Product product = Product.create("Switch 2", "설명", 689000L,
                "Nintendo", "게이밍 기기", null);
        productRepository.save(product);
        Inventory inventory = Inventory.create(product.getProductId(), 10);
        inventoryRepository.save(inventory);
        stubDropServiceTimeout();

        String request = """
                { "totalQuantity": 20, "reason": "입고 추가" }
                """;

        mockMvc.perform(patch("/api/v1/admin/products/{productId}/inventories",
                        product.getProductId())
                        .header("X-Gateway-Secret", GW_SECRET)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("PRODUCT-020"));
    }
}