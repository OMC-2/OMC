package com.omc.coupon.application.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.coupon.application.event.dto.inbound.CouponIssueRequestedEvent;
import com.omc.coupon.application.service.CouponIssueWriterService;
import com.omc.coupon.infrastructure.kafka.KafkaTopics;
import com.omc.coupon.infrastructure.redis.CouponRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueRequestedConsumer {

    private final CouponIssueWriterService writer;
    private final CouponRedisRepository couponRedisRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.COUPON_ISSUE_REQUESTED, groupId = "coupon-service")
    public void handle(
            String message,
            Acknowledgment acknowledgment,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        CouponIssueRequestedEvent event = readValue(message, CouponIssueRequestedEvent.class, topic);
        log.info("[CouponIssueRequestedConsumer] 수신. topic={}, offset={}, couponId={}, userId={}",
                topic, offset, event.couponId(), event.userId());

        // 1차 멱등성: Redis 발급 기록 확인 — Producer 실패 후 롤백된 경우 skip
        if (!couponRedisRepository.isAlreadyIssued(event.couponId().toString(), event.userId().toString())) {
            log.info("[CouponIssueRequestedConsumer] Redis 발급 기록 없음 — 롤백된 요청으로 skip. couponId={}, userId={}",
                    event.couponId(), event.userId());
            acknowledgment.acknowledge();
            return;
        }

        try {
            writer.write(event);
        } catch (DataIntegrityViolationException e) {
            // 2차 멱등성: DB UNIQUE 위반 — 이미 발급된 쿠폰 (at-least-once 재처리)
            log.warn("[CouponIssueRequestedConsumer] DB UNIQUE 위반 — 중복 처리 skip. couponId={}, userId={}",
                    event.couponId(), event.userId());
            acknowledgment.acknowledge();
            return;
        }

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
