package com.omc.coupon.application.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.coupon.application.event.dto.inbound.PaymentFailedEvent;
import com.omc.coupon.application.service.CouponSagaService;
import com.omc.coupon.infrastructure.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFailedConsumer {

    private final CouponSagaService couponSagaService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.PAYMENT_FAILED, groupId = "coupon-service")
    public void handle(
            String message,
            Acknowledgment acknowledgment,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        PaymentFailedEvent event = readValue(message, PaymentFailedEvent.class, topic);
        log.info("[PaymentFailedConsumer] 수신. topic={}, offset={}, eventId={}", topic, offset, event.eventId());
        couponSagaService.restoreCoupon(event.eventId(), topic, event.orderId());
        acknowledgment.acknowledge();
    }

    private <T> T readValue(String message, Class<T> targetType, String topic) {
        try {
            return objectMapper.readValue(message, targetType);
        } catch (Exception e) {
            log.error("{} 이벤트 역직렬화 실패. payload={}", topic, message, e);
            throw new IllegalStateException(topic + " 이벤트 역직렬화 실패", e);
        }
    }
}
