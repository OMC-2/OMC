package com.omc.product.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.common.util.UuidV7Generator;
import com.omc.product.application.event.producer.StockDeductedEvent;
import com.omc.product.application.event.StockFailedEvent;
import com.omc.product.application.event.PaymentCompletedEvent;
import com.omc.product.domain.entity.FailedEventLog;
import com.omc.product.domain.entity.Inventory;
import com.omc.product.domain.entity.OutboxEvent;
import com.omc.product.domain.entity.ProcessedEvent;
import com.omc.product.domain.exception.ActiveDropExistsException;
import com.omc.product.domain.exception.InsufficientStockException;
import com.omc.product.domain.exception.InventoryNotFoundException;
import com.omc.product.domain.enums.OutboxEventType;
import com.omc.product.domain.repository.*;
import com.omc.product.application.event.ProductUpdatedEvent;
import com.omc.product.infrastructure.client.ActiveDropResponse;
import com.omc.product.infrastructure.client.DropInternalClient;
import com.omc.product.infrastructure.kafka.KafkaTopics;
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
 * - 낙관적 락 충돌로 재고 차감에 실패하면 STOCK_FAILED 이벤트를 발행
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

    private static final String CONSUMER_GROUP = "product-service";

    private final InventoryRepository inventoryRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final FailedEventLogRepository failedEventLogRepository;
    private final ObjectMapper objectMapper;
    private final DropInternalClient dropInternalClient;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void confirmDeduct(PaymentCompletedEvent event) {

        if (processedEventRepository.existsByEventId(event.eventId())) {
            log.info("[InventoryService] 이미 처리된 이벤트 스킵. eventId={}", event.eventId());
            return;
        }

        Inventory inventory = inventoryRepository.findByProductId(event.productId())
                .orElseThrow(InventoryNotFoundException::new);

        try {
            UUID outboxEventId = UuidV7Generator.generate();

            inventory.confirmDeduct(1);

            saveOutbox(outboxEventId, "INVENTORY", inventory.getInventoryId(),
                    OutboxEventType.STOCK_DEDUCTED, buildPayload(event, outboxEventId));
            processedEventRepository.save(
                    ProcessedEvent.create(event.eventId(), KafkaTopics.PAYMENT_COMPLETED)
            );

            log.info("[InventoryService] 재고 확정 차감 완료. productId={}, orderId={}",
                    event.productId(), event.orderId());

        } catch (ObjectOptimisticLockingFailureException e) {

            log.error("[InventoryService] 재고 차감 실패 (버전 충돌). productId={}", event.productId());
            handleStockFailure(inventory, event, e.getMessage()
            );
        } catch (InsufficientStockException e) {
            log.error("[InventoryService] 재고 차감 실패 (재고 부족). productId={}", event.productId());
            handleStockFailure(inventory, event, e.getMessage());
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

    private void saveOutbox(UUID eventId, String aggregateType, UUID aggregateId,
                            OutboxEventType eventType, String payload) {
        outboxEventRepository.save(
                OutboxEvent.create(eventId, aggregateType, aggregateId, eventType, payload)
        );
    }

    private String buildPayload(PaymentCompletedEvent event, UUID eventId) {
        return toJson(new StockDeductedEvent(
                eventId.toString(),
                event.orderId(),
                event.productId(),
                event.userId(),
                event.dropId()
        ));
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String buildFailedPayload(PaymentCompletedEvent event, UUID eventId) {
        return toJson(new StockFailedEvent(
                eventId.toString(),
                event.orderId(),
                event.productId(),
                event.dropId(),
                event.userId()
        ));
    }

    private void handleStockFailure(Inventory inventory,
                                    PaymentCompletedEvent event,
                                    String errorMessage) {
        UUID outboxEventId = UuidV7Generator.generate();

        failedEventLogRepository.save(
                FailedEventLog.create(
                        KafkaTopics.PAYMENT_COMPLETED,
                        CONSUMER_GROUP,
                        "INVENTORY",
                        inventory.getInventoryId(),
                        toJson(event),
                        errorMessage
                )
        );

        saveOutbox(outboxEventId, "INVENTORY", inventory.getInventoryId(),
                OutboxEventType.STOCK_FAILED, buildFailedPayload(event, outboxEventId));
    }
}