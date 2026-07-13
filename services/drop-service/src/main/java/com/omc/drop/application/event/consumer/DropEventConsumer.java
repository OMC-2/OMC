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
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DropEventConsumer {

    private static final String DROP = "DROP";

    private final ObjectMapper objectMapper;
    private final HoldService holdService;
    private final DropProcessedEventRepository processedEventRepository;

    @Transactional
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

    @Transactional
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

    @Transactional
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

    // existsById로 1차 체크 후 saveAndFlush로 삽입한다.
    // holdService 실패 → 외부 TX 롤백 → INSERT 롤백 → Kafka 재시도 시 existsById=false → 정상 재처리.
    // saveAndFlush에서 DataIntegrityViolationException이 발생하면 동시 삽입으로 판단하고 중복 처리한다.
    private boolean isDuplicate(String eventId, String topic) {
        if (processedEventRepository.existsById(eventId)) {
            log.info("중복 이벤트 스킵. topic={}, eventId={}", topic, eventId);
            return true;
        }
        try {
            processedEventRepository.saveAndFlush(DropProcessedEvent.of(eventId, topic));
        } catch (DataIntegrityViolationException e) {
            log.info("중복 이벤트 스킵 (동시 삽입). topic={}, eventId={}", topic, eventId);
            return true;
        }
        return false;
    }

    private boolean isNotDrop(String salesType, String eventId, String topic) {
        if (!DROP.equals(salesType)) {
            log.info("RAFFLE 이벤트 스킵. topic={}, eventId={}", topic, eventId);
            return true;
        }
        return false;
    }
}
