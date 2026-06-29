package com.omc.product.application.processor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.common.util.UuidV7Generator;
import com.omc.product.application.event.PaymentCompletedEvent;
import com.omc.product.application.event.producer.StockDeductedEvent;
import com.omc.product.domain.entity.OutboxEvent;
import com.omc.product.domain.entity.ProcessedEvent;
import com.omc.product.domain.enums.OutboxEventType;
import com.omc.product.domain.repository.OutboxEventRepository;
import com.omc.product.domain.repository.ProcessedEventRepository;
import com.omc.product.infrastructure.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 재고 차감 성공 처리 컴포넌트
 *
 * REQUIRES_NEW 트랜잭션 적용 이유:
 * InventoryDeductProcessor.tryDeduct()가 성공(커밋)한 이후
 * ProcessedEvent, STOCK_DEDUCTED Outbox를 독립 트랜잭션으로 저장
 * 독립 트랜잭션으로 분리하지 않으면 이 저장이 실패할 경우 롤백되어
 * ProcessedEvent가 없는 상태에서 재처리 시 중복 차감이 발생할 수 있음
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockSuccessHandler {

    private final OutboxEventRepository outboxEventRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(UUID inventoryId, PaymentCompletedEvent event) {
        UUID outboxEventId = UuidV7Generator.generate();

        outboxEventRepository.save(
                OutboxEvent.create(
                        outboxEventId,
                        "INVENTORY",
                        inventoryId,
                        OutboxEventType.STOCK_DEDUCTED,
                        buildPayload(event, outboxEventId)
                )
        );

        processedEventRepository.save(
                ProcessedEvent.create(event.eventId(), KafkaTopics.PAYMENT_COMPLETED)
        );

        log.info("[StockSuccessHandler] 성공 처리 완료. productId={}, orderId={}",
                event.productId(), event.orderId());
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
}
