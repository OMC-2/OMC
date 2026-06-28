package com.omc.product.application.service;

import com.omc.product.application.event.PaymentCompletedEvent;
import com.omc.product.application.event.ProductUpdatedEvent;
import com.omc.product.domain.entity.Inventory;
import com.omc.product.domain.exception.ActiveDropExistsException;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 재고 관리 서비스
 *
 * 주요 책임
 * - 상품 재고 조회 및 수정
 * - payment.completed 이벤트 기반 재고 확정 차감
 * - Outbox 패턴을 통한 재고 이벤트 발행
 * - ProcessedEvent를 이용한 이벤트 멱등성 보장
 *
 * 재고 확정 차감 처리
 * - payment.completed 이벤트 수신 시 재고를 확정 차감
 * - 동일 eventId는 한 번만 처리
 * - 차감 성공 시 STOCK_DEDUCTED 이벤트를 Outbox에 저장
 *
 * SAGA 보상 처리
 * - 낙관적 락 충돌 또는 재고 부족으로 차감 실패 시 STOCK_FAILED 이벤트를 발행
 * - Payment Service는 해당 이벤트를 수신하여 환불 등 보상 트랜잭션을 수행
 *
 * 재고 수정 정책
 * - 진행 중인 Drop(SCHEDULED, OPEN)이 존재하는 상품은 재고 수정이 불가능
 * - Drop Service Internal API를 통해 활성 Drop 여부를 확인
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

    /**
     * 재고 확정 차감 (payment.completed 이벤트 수신 시 호출)
     *
     * 트랜잭션 설계:
     * ① InventoryDeductProcessor.tryDeduct() [REQUIRES_NEW]
     *    - inventory UPDATE → 즉시 커밋
     *    - ObjectOptimisticLockingFailureException 발생 시 호출부로 전파
     * ② StockSuccessHandler.handle() [REQUIRES_NEW]
     *    - ProcessedEvent + STOCK_DEDUCTED Outbox 저장
     *    - ①과 독립 트랜잭션 → ①성공 후 ②실패해도 재처리 시 ①은 유지
     * ③ StockFailureHandler.handle() [REQUIRES_NEW]
     *    - FailedEventLog + STOCK_FAILED Outbox 저장
     *    - ①롤백과 무관하게 독립 커밋
     */
    public void confirmDeduct(PaymentCompletedEvent event) {

        if (processedEventRepository.existsByEventId(event.eventId())) {
            log.info("[InventoryService] 이미 처리된 이벤트 스킵. eventId={}", event.eventId());
            return;
        }

        try {
            // ① 재고 차감 — REQUIRES_NEW로 즉시 커밋 → 충돌 시 예외 발생
            UUID inventoryId = inventoryDeductProcessor.tryDeduct(event.productId());

            // ② 성공 처리 — ProcessedEvent + STOCK_DEDUCTED Outbox 저장
            stockSuccessHandler.handle(inventoryId, event);

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
