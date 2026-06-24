package com.omc.drop.application.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.drop.application.service.HoldService;
import com.omc.drop.domain.entity.DropProcessedEvent;
import com.omc.drop.domain.repository.DropProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DropEventConsumer {

    private static final String INSTANT = "INSTANT";

    private final ObjectMapper objectMapper;
    private final HoldService holdService;
    private final DropProcessedEventRepository processedEventRepository;

    @KafkaListener(topics = "payment.completed", groupId = "drop-service")
    public void onPaymentCompleted(String message) {
        try {
            PaymentCompletedEvent event = objectMapper.readValue(message, PaymentCompletedEvent.class);
            if (isDuplicate(event.eventId(), "payment.completed")) return;
            if (isNotInstant(event.salesType(), event.eventId(), "payment.completed")) return;

            holdService.confirmHold(event.dropId(), event.orderId(), event.userId());
            markProcessed(event.eventId(), "payment.completed");

        } catch (Exception e) {
            log.error("payment.completed 처리 실패. message={}", message, e);
            throw new EventProcessingException("payment.completed", e);
        }
    }

    @KafkaListener(topics = "payment.failed", groupId = "drop-service")
    public void onPaymentFailed(String message) {
        try {
            PaymentFailedEvent event = objectMapper.readValue(message, PaymentFailedEvent.class);
            if (isDuplicate(event.eventId(), "payment.failed")) return;
            if (isNotInstant(event.salesType(), event.eventId(), "payment.failed")) return;

            holdService.recoverHold(event.dropId(), event.orderId(), event.userId());
            markProcessed(event.eventId(), "payment.failed");

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
            markProcessed(event.eventId(), "stock.failed");

        } catch (Exception e) {
            log.error("stock.failed 처리 실패. message={}", message, e);
            throw new EventProcessingException("stock.failed", e);
        }
    }

    private boolean isDuplicate(String eventId, String topic) {
        if (processedEventRepository.existsById(eventId)) {
            log.info("중복 이벤트 스킵. topic={}, eventId={}", topic, eventId);
            return true;
        }
        return false;
    }

    private boolean isNotInstant(String salesType, String eventId, String topic) {
        if (!INSTANT.equals(salesType)) {
            log.info("RAFFLE 이벤트 스킵. topic={}, eventId={}", topic, eventId);
            return true;
        }
        return false;
    }

    private void markProcessed(String eventId, String topic) {
        processedEventRepository.save(DropProcessedEvent.of(eventId, topic));
    }
}
