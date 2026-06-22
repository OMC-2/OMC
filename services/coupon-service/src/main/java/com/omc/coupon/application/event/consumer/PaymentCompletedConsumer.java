package com.omc.coupon.application.event.consumer;

import com.omc.coupon.application.service.CouponSagaService;
import com.omc.coupon.infrastructure.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCompletedConsumer {

    private final CouponSagaService couponSagaService;

    @KafkaListener(topics = KafkaTopics.PAYMENT_COMPLETED, groupId = "coupon-service")
    public void handle(
            @Payload Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        String eventId = String.valueOf(event.get("eventId"));
        log.info("[PaymentCompletedConsumer] 수신. topic={}, offset={}, eventId={}", topic, offset, eventId);

        String orderId = String.valueOf(event.get("orderId"));
        couponSagaService.confirmCoupon(eventId, topic, UUID.fromString(orderId));
    }
}
