package com.omc.coupon.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.coupon.application.event.dto.inbound.CouponIssueRequestedEvent;
import com.omc.coupon.infrastructure.redis.CouponRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final CouponRedisRepository couponRedisRepository;
    private final ObjectMapper objectMapper;

    public void publish(UUID couponId, UUID userId) {
        CouponIssueRequestedEvent event = new CouponIssueRequestedEvent(couponId, userId, LocalDateTime.now());
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("CouponIssueRequestedEvent 직렬화 실패", e);
        }

        kafkaTemplate.send(KafkaTopics.COUPON_ISSUE_REQUESTED, couponId.toString(), payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[CouponIssueProducer] Kafka 발행 실패 — Redis 롤백. couponId={}, userId={}", couponId, userId, ex);
                        couponRedisRepository.incrementStock(couponId.toString());
                        couponRedisRepository.removeIssued(couponId.toString(), userId.toString());
                    }
                });
    }
}
