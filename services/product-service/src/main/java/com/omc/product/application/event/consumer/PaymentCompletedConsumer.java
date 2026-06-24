package com.omc.product.application.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.product.application.event.PaymentCompletedEvent;
import com.omc.product.application.service.InventoryService;
import com.omc.product.infrastructure.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCompletedConsumer {

    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics =  KafkaTopics.PAYMENT_COMPLETED,
            groupId = "product-service"
    )
    public void handlePaymentCompleted(
            String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.info("[PaymentCompletedConsumer] 수신. topic={}, offset={}", topic, offset);

        try {
            PaymentCompletedEvent event = objectMapper.readValue(message, PaymentCompletedEvent.class);
            log.info("[PaymentCompletedConsumer] 역직렬화 완료. eventId={}", event.eventId());
            inventoryService.confirmDeduct(event);
        } catch (Exception e) {
            log.error("[PaymentCompletedConsumer] 처리 중 예외 발생. message={}, error={}",
                    message, e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}