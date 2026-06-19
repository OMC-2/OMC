package com.omc.product.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.product.application.event.dto.request.PaymentCompletedRequest;
import com.omc.product.domain.entity.FailedEventLog;
import com.omc.product.domain.entity.Inventory;
import com.omc.product.domain.entity.OutboxEvent;
import com.omc.product.domain.entity.ProcessedEvent;
import com.omc.product.domain.exception.InventoryNotFoundException;
import com.omc.product.domain.enums.OutboxEventType;
import com.omc.product.domain.repository.*;
import com.omc.product.presentation.dto.request.InventoryUpdateRequest;
import com.omc.product.presentation.dto.response.InventoryResponse;
import com.omc.product.presentation.dto.response.InventorySnapshotResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InventoryService {

    private static final String CONSUMER_GROUP = "product-service";
    private static final String TOPIC_PAYMENT_COMPLETED = "payment.completed";
    private static final int MAX_RETRY = 3;

    private final InventoryRepository inventoryRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final FailedEventLogRepository failedEventLogRepository;
    private final ObjectMapper objectMapper;

    // 재고 확정 차감 (payment.completed 이벤트 수신 시 호출)
    @Transactional
    public void confirmDeduct(PaymentCompletedRequest event) {
        // 1. 멱등성 확인
        if (processedEventRepository.existsByEventId(event.eventId())) {
            log.info("[InventoryService] 이미 처리된 이벤트 스킵. eventId={}", event.eventId());
            return;
        }

        Inventory inventory = inventoryRepository.findByProductId(event.productId())
                .orElseThrow(InventoryNotFoundException::new);

        try {
            // 2. DB 재고 확정 차감 (@Version 낙관적 락)
            inventory.confirmDeduct(event.quantity());

            // 3. Outbox INSERT (STOCK_DEDUCTED) + 멱등성 키 저장 — 같은 트랜잭션
            saveOutbox("INVENTORY", inventory.getInventoryId(),
                    OutboxEventType.STOCK_DEDUCTED, buildPayload(event));
            processedEventRepository.save(
                    ProcessedEvent.create(event.eventId(), TOPIC_PAYMENT_COMPLETED)
            );

            log.info("[InventoryService] 재고 확정 차감 완료. productId={}, orderId={}",
                    event.productId(), event.orderId());

        } catch (ObjectOptimisticLockingFailureException e) {
            // 4. 낙관적 락 실패 → SAGA 케이스 B 트리거
            log.error("[InventoryService] 재고 차감 실패 (버전 충돌). productId={}, orderId={}",
                    event.productId(), event.orderId());

            // 실패 로그 기록
            failedEventLogRepository.save(
                    FailedEventLog.create(
                            TOPIC_PAYMENT_COMPLETED,
                            CONSUMER_GROUP,
                            "INVENTORY",
                            inventory.getInventoryId(),
                            toJson(event),
                            e.getMessage()
                    )
            );

            // stock.failed Outbox INSERT → Poller가 Kafka 발행 → Payment Service 환불 트리거
            saveOutbox("INVENTORY", inventory.getInventoryId(),
                    OutboxEventType.STOCK_FAILED, buildPayload(event));
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
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(InventoryNotFoundException::new);
        inventory.updateTotalQuantity(request.totalQuantity());
        return InventoryResponse.from(inventory);
    }

    private void saveOutbox(String aggregateType, UUID aggregateId,
                            OutboxEventType eventType, String payload) {
        outboxEventRepository.save(
                OutboxEvent.create(aggregateType, aggregateId, eventType, payload)
        );
    }

    private String buildPayload(PaymentCompletedRequest event) {
        return toJson(event);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}