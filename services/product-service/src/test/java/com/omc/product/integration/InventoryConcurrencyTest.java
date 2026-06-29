package com.omc.product.integration;

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
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 재고 동시성 테스트
 *
 * 시나리오: 래플 당첨자 일괄 처리 시 여러 스레드가 동시에 sold_quantity +1 시도
 * → @Version 낙관적 락 충돌 발생
 * → 일부는 STOCK_DEDUCTED, 일부는 STOCK_FAILED 발행 검증
 *
 * 사전 조건:
 * - AbstractIntegrationTest에서 payment.completed 파티션 3개 생성
 * - spring.kafka.listener.concurrency = 3 (테스트 환경 전용)
 * → 3개 스레드가 각 파티션을 담당해 동시 처리 → 낙관적 락 충돌 재현
 *
 * 검증 항목:
 * 1. 최종 soldQuantity가 totalQuantity(5)를 초과하지 않음 (재고 정확성)
 * 2. STOCK_DEDUCTED + STOCK_FAILED = 전체 요청 수 10 (이벤트 누락 없음)
 * 3. soldQuantity == STOCK_DEDUCTED 수 (차감 수량 정확성)
 * 4. STOCK_FAILED 최소 1개 이상 (낙관적 락 충돌 재현 확인)
 */
class InventoryConcurrencyTest extends AbstractIntegrationTest {

    @Autowired ProductRepository productRepository;
    @Autowired InventoryRepository inventoryRepository;
    @Autowired OutboxEventRepository outboxEventRepository;
    @Autowired ProcessedEventRepository processedEventRepository;
    @Autowired KafkaTemplate<String, String> kafkaTemplate;

    private Product savedProduct;
    private static final int TOTAL_QUANTITY = 5;
    private static final int CONCURRENT_REQUESTS = 10;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        processedEventRepository.deleteAll();
        inventoryRepository.deleteAll();
        productRepository.deleteAll();

        savedProduct = Product.create("Switch 2", "설명", 689000L,
                "Nintendo", "게이밍 기기", null);
        productRepository.save(savedProduct);

        Inventory inventory = Inventory.create(savedProduct.getProductId(), TOTAL_QUANTITY);
        inventoryRepository.save(inventory);
    }

    @Test
    void 동시_재고_차감_시_낙관적_락_충돌로_STOCK_FAILED_발행() {
        List<String> eventIds = IntStream.range(0, CONCURRENT_REQUESTS)
                .mapToObj(i -> UUID.randomUUID().toString())
                .toList();

        String dropId = UUID.randomUUID().toString();

        List<CompletableFuture<Void>> futures = eventIds.stream()
                .map(eventId -> CompletableFuture.runAsync(() -> {
                    String payload = String.format(
                        """   
                        {
                            "eventId": "%s",
                            "orderId": "%s",
                            "productId": "%s",
                            "userId": "%s",
                            "dropId": "%s",
                            "finalAmount": 689000
                        }
                        """,
                        eventId,
                        UUID.randomUUID(),
                        savedProduct.getProductId(),
                        UUID.randomUUID(),
                        dropId
                    );
                    kafkaTemplate.send("payment.completed", eventId, payload);
                }))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        Awaitility.await()
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    long deducted = outboxEventRepository.findAll().stream()
                            .filter(e -> e.getEventType() == OutboxEventType.STOCK_DEDUCTED
                                    && e.getStatus() == OutboxStatus.PUBLISHED)
                            .count();
                    long failed = outboxEventRepository.findAll().stream()
                            .filter(e -> e.getEventType() == OutboxEventType.STOCK_FAILED
                                    && e.getStatus() == OutboxStatus.PUBLISHED)
                            .count();

                    assertThat(deducted + failed).isEqualTo(CONCURRENT_REQUESTS);

                    Inventory updated = inventoryRepository
                            .findByProductId(savedProduct.getProductId()).orElseThrow();
                    assertThat(updated.getSoldQuantity()).isLessThanOrEqualTo(TOTAL_QUANTITY);
                    assertThat(updated.getSoldQuantity()).isEqualTo((int) deducted);
                    assertThat(failed).isGreaterThanOrEqualTo(1);
                });
    }
}
