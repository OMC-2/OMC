package com.omc.drop.application.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.drop.application.service.HoldService;
import com.omc.drop.domain.entity.DropProcessedEvent;
import com.omc.drop.domain.repository.DropProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DropEventConsumer {

    private static final String DROP = "DROP";

    private final ObjectMapper objectMapper;
    private final HoldService holdService;
    private final DropProcessedEventRepository processedEventRepository;

    @KafkaListener(topics = "payment.completed", groupId = "drop-service")
    public void onPaymentCompleted(String message) {
        try {
            PaymentCompletedEvent event = objectMapper.readValue(message, PaymentCompletedEvent.class);
            if (isNotDrop(event.salesType(), event.eventId(), "payment.completed")) return;
            if (isDuplicate(event.eventId(), "payment.completed")) return;

            holdService.confirmHold(event.dropId(), event.orderId(), event.userId());

        } catch (Exception e) {
            log.error("payment.completed 처리 실패. message={}", message, e);
            throw new EventProcessingException("payment.completed", e);
        }
    }

    @KafkaListener(topics = "payment.failed", groupId = "drop-service")
    public void onPaymentFailed(String message) {
        try {
            PaymentFailedEvent event = objectMapper.readValue(message, PaymentFailedEvent.class);
            if (isNotDrop(event.salesType(), event.eventId(), "payment.failed")) return;
            if (isDuplicate(event.eventId(), "payment.failed")) return;

            holdService.recoverHold(event.dropId(), event.orderId(), event.userId());

        } catch (Exception e) {
            log.error("payment.failed 처리 실패. message={}", message, e);
            throw new EventProcessingException("payment.failed", e);
        }
    }

    @KafkaListener(topics = "stock.failed", groupId = "drop-service")
    public void onStockFailed(String message) {
        try {
            StockFailedEvent event = objectMapper.readValue(message, StockFailedEvent.class);
            if (isDuplicate(event.eventId(), "stock.failed")) return;

            holdService.recoverHold(event.dropId(), event.orderId(), event.userId());

        } catch (Exception e) {
            log.error("stock.failed 처리 실패. message={}", message, e);
            throw new EventProcessingException("stock.failed", e);
        }
    }

    // DB PK 제약을 lock으로 활용해 원자적으로 중복 처리를 방지한다.
    // SELECT → INSERT 패턴(기존)은 두 인스턴스가 동시에 SELECT를 통과할 수 있어 Race Condition이 발생한다.
    private boolean isDuplicate(String eventId, String topic) {
        try {
            processedEventRepository.save(DropProcessedEvent.of(eventId, topic));
            return false;
        } catch (DataIntegrityViolationException e) {
            log.info("중복 이벤트 스킵. topic={}, eventId={}", topic, eventId);
            return true;
        }
    }

    private boolean isNotDrop(String salesType, String eventId, String topic) {
        if (!DROP.equals(salesType)) {
            log.info("RAFFLE 이벤트 스킵. topic={}, eventId={}", topic, eventId);
            return true;
        }
        return false;
    }
}
