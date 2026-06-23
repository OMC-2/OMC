package com.omc.coupon.application.event.producer;

import com.omc.coupon.infrastructure.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void publishCouponIssued(UUID couponId, UUID userId) {
        String payload = String.format(
                "{\"eventId\":\"%s\",\"couponId\":\"%s\",\"userId\":\"%s\"}",
                UUID.randomUUID(), couponId, userId
        );
        kafkaTemplate.send(KafkaTopics.COUPON_ISSUED, couponId.toString(), payload);
        log.info("[CouponEventProducer] coupon.issued 발행. couponId={}, userId={}", couponId, userId);
    }

    public void publishCouponUsed(UUID couponId, UUID userId, UUID orderId) {
        String payload = String.format(
                "{\"eventId\":\"%s\",\"couponId\":\"%s\",\"userId\":\"%s\",\"orderId\":\"%s\"}",
                UUID.randomUUID(), couponId, userId, orderId
        );
        kafkaTemplate.send(KafkaTopics.COUPON_USED, couponId.toString(), payload);
        log.info("[CouponEventProducer] coupon.used 발행. couponId={}, userId={}, orderId={}", couponId, userId, orderId);
    }
}
