package com.omc.product.application.event.consumer;

import com.omc.product.application.event.dto.request.PaymentCompletedRequest;
import com.omc.product.application.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCompletedConsumer {

    private final InventoryService inventoryService;

    @KafkaListener(
            topics = "payment.completed",
            groupId = "product-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentCompleted(
            @Payload PaymentCompletedRequest event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.info("[PaymentCompletedConsumer] 수신. topic={}, offset={}, eventId={}",
                topic, offset, event.eventId());

        try {
            inventoryService.confirmDeduct(event);
        } catch (Exception e) {
            log.error("[PaymentCompletedConsumer] 처리 중 예외 발생. eventId={}, error={}",
                    event.eventId(), e.getMessage(), e);
            // 재시도는 Spring Kafka RetryTemplate 또는 ErrorHandler에서 처리
            throw e;
        }
    }
}