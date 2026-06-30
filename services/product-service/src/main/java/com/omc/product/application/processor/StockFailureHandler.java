package com.omc.product.application.processor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.common.util.UuidV7Generator;
import com.omc.product.application.event.PaymentCompletedEvent;
import com.omc.product.application.event.StockFailedEvent;
import com.omc.product.domain.entity.FailedEventLog;
import com.omc.product.domain.entity.OutboxEvent;
import com.omc.product.domain.enums.OutboxEventType;
import com.omc.product.domain.repository.FailedEventLogRepository;
import com.omc.product.domain.repository.OutboxEventRepository;
import com.omc.product.infrastructure.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 재고 차감 실패 처리 컴포넌트
 *
 * REQUIRES_NEW 트랜잭션 적용 이유:
 * InventoryService.confirmDeduct()는 REQUIRES_NEW 트랜잭션으로 동작
 * InsufficientStockException 발생 시 부모 트랜잭션이 롤백되면
 * 같은 트랜잭션 내의 FailedEventLog, OutboxEvent INSERT도 함께 롤백
 * REQUIRES_NEW로 독립 트랜잭션을 열면 부모 롤백과 무관하게 실패 처리가 커밋
 *
 * self-invocation 방지:
 * InventoryService 내부에서 직접 호출하면 Spring AOP 프록시를 우회하여
 * REQUIRES_NEW가 동작하지 않으므로 별도 컴포넌트로 분리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockFailureHandler {

    private static final String CONSUMER_GROUP = "product-service";

    private final FailedEventLogRepository failedEventLogRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(UUID inventoryId, PaymentCompletedEvent event, String errorMessage) {
        UUID outboxEventId = UuidV7Generator.generate();

        failedEventLogRepository.save(
                FailedEventLog.create(
                        KafkaTopics.PAYMENT_COMPLETED,
                        CONSUMER_GROUP,
                        "INVENTORY",
                        inventoryId,
                        toJson(event),
                        errorMessage
                )
        );

        outboxEventRepository.save(
                OutboxEvent.create(
                        outboxEventId,
                        "INVENTORY",
                        inventoryId,
                        OutboxEventType.STOCK_FAILED,
                        buildFailedPayload(event, outboxEventId)
                )
        );

        log.info("[StockFailureHandler] 실패 처리 완료. productId={}, orderId={}",
                event.productId(), event.orderId());
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

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
