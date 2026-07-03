package com.omc.product.application.service;

import com.omc.product.application.event.PaymentCompletedEvent;
import com.omc.product.application.event.producer.ProductUpdatedEvent;
import com.omc.product.application.processor.InventoryDeductProcessor;
import com.omc.product.application.processor.StockFailureHandler;
import com.omc.product.application.processor.StockSuccessHandler;
import com.omc.product.domain.entity.Inventory;
import com.omc.product.domain.exception.ActiveDropExistsException;
import com.omc.product.domain.exception.DeductionBusyException;
import com.omc.product.domain.exception.InsufficientStockException;
import com.omc.product.domain.exception.InventoryNotFoundException;
import com.omc.product.domain.repository.InventoryRepository;
import com.omc.product.domain.repository.ProcessedEventRepository;
import com.omc.product.infrastructure.client.ActiveDropResponse;
import com.omc.product.infrastructure.client.DropInternalClient;
import com.omc.product.presentation.dto.request.InventoryUpdateRequest;
import com.omc.product.presentation.dto.response.InventoryResponse;
import com.omc.product.presentation.dto.response.InventorySnapshotResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 재고 관리 서비스
 *
 * - 상품 재고 조회/수정
 * - payment.completed 이벤트 기반 재고 확정 차감 (낙관적 락 충돌·재고 부족 시 SAGA 보상)
 * - 진행 중인 Drop(SCHEDULED, OPEN)이 있는 상품은 재고 수동 수정 불가
 *
 * 재고 확정 차감 (payment.completed 이벤트 수신 시 호출):
 * 재고 차감과 결과 기록(Outbox)을 REQUIRES_NEW로 분리해, 차감 성공 후
 * 결과 기록이 실패해도 재고 차감 자체는 롤백되지 않도록 함
 *
 * 동시성 방어막 (Bulkhead): confirmDeduct() 동시 실행이 HikariCP 풀 크기를 넘으면
 * 커넥션 고갈로 전멸하는 현상이 실측 확인됨(근본 원인 미상). Semaphore로 동시 실행 수를 제한하고,
 * 허가를 못 받으면 DeductionBusyException으로 즉시 실패시켜 Kafka 재시도로 위임
 * 상세 배경: InventoryConcurrentHttpBypassLoadTest 참고
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final DropInternalClient dropInternalClient;
    private final ApplicationEventPublisher eventPublisher;
    private final InventoryDeductProcessor inventoryDeductProcessor;
    private final StockSuccessHandler stockSuccessHandler;
    private final StockFailureHandler stockFailureHandler;

    @Value("${inventory.deduct.max-concurrency:3}")
    private int maxConcurrency = 3; // 순수 단위테스트에선 @Value가 주입 안 돼 0으로 남는 것을 방지
    @Value("${inventory.deduct.acquire-timeout-ms:2000}")
    private long acquireTimeoutMs = 2000;
    private Semaphore deductPermits;

    private Semaphore deductPermits() {
        if (deductPermits == null) {
            deductPermits = new Semaphore(maxConcurrency);
        }
        return deductPermits;
    }

    public void confirmDeduct(PaymentCompletedEvent event) {

        boolean acquired;
        try {
            acquired = deductPermits().tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[InventoryService] 세마포어 대기 중 인터럽트. eventId={}", event.eventId());
            throw new DeductionBusyException();
        }
        if (!acquired) {
            log.warn("[InventoryService] 동시성 한도 초과로 처리 지연. eventId={}, productId={}, maxConcurrency={}",
                    event.eventId(), event.productId(), maxConcurrency);
            throw new DeductionBusyException(); // Kafka 리스너까지 전파 → 재시도
        }

        try {
            if (processedEventRepository.existsByEventId(event.eventId())) {
                log.info("[InventoryService] 이미 처리된 이벤트 스킵. eventId={}", event.eventId());
                return;
            }

            UUID inventoryId = inventoryDeductProcessor.tryDeduct(event.productId());
            stockSuccessHandler.handle(inventoryId, event);
            eventPublisher.publishEvent(new ProductUpdatedEvent(event.productId())); // 재고 변경 → 캐시 무효화

            log.info("[InventoryService] 재고 확정 차감 완료. productId={}, orderId={}",
                    event.productId(), event.orderId());

        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("[InventoryService] 재고 차감 실패 (버전 충돌). productId={}", event.productId());
            UUID inventoryId = inventoryRepository.findByProductId(event.productId())
                    .orElseThrow(InventoryNotFoundException::new).getInventoryId();
            stockFailureHandler.handle(inventoryId, event, e.getMessage());

        } catch (InsufficientStockException e) {
            log.error("[InventoryService] 재고 차감 실패 (재고 부족). productId={}", event.productId());
            UUID inventoryId = inventoryRepository.findByProductId(event.productId())
                    .orElseThrow(InventoryNotFoundException::new).getInventoryId();
            stockFailureHandler.handle(inventoryId, event, e.getMessage());

        } finally {
            deductPermits().release();
        }
    }

    public InventorySnapshotResponse getSnapshot(UUID productId) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(InventoryNotFoundException::new);
        return InventorySnapshotResponse.from(inventory);
    }

    public InventoryResponse getInventory(UUID productId) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(InventoryNotFoundException::new);
        return InventoryResponse.from(inventory);
    }

    @Transactional
    public InventoryResponse updateInventory(UUID productId, InventoryUpdateRequest request) {
        ActiveDropResponse activeDropResponse = dropInternalClient.hasActiveDrop(productId).getData();
        if (activeDropResponse.hasActiveDrop()) {
            throw new ActiveDropExistsException();
        }
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(InventoryNotFoundException::new);
        inventory.updateTotalQuantity(request.totalQuantity());

        log.info("[InventoryService] 재고 수동 수정. productId={}, totalQuantity={}, reason={}",
                productId, request.totalQuantity(), request.reason());

        eventPublisher.publishEvent(new ProductUpdatedEvent(productId));

        return InventoryResponse.from(inventory);
    }
}
