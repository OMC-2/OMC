package com.omc.coupon.application.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.coupon.application.event.dto.inbound.CouponIssueRequestedEvent;
import com.omc.coupon.application.service.CouponIssueWriterService;
import com.omc.coupon.infrastructure.kafka.KafkaTopics;
import com.omc.coupon.infrastructure.redis.CouponRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueRequestedConsumer {

    private final CouponIssueWriterService writer;
    private final CouponRedisRepository couponRedisRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.COUPON_ISSUE_REQUESTED, groupId = "coupon-service")
    public void handle(
            List<ConsumerRecord<String, String>> records,
            Acknowledgment acknowledgment
    ) {
        log.info("[CouponIssueRequestedConsumer] 수신 배치 크기: {}", records.size());

        List<CouponIssueRequestedEvent> validEvents = new ArrayList<>();

        for (ConsumerRecord<String, String> record : records) {
            String message = record.value();
            try {
                CouponIssueRequestedEvent event = objectMapper.readValue(message, CouponIssueRequestedEvent.class);

                // 1차 멱등성: Redis 발급 기록 확인 — Producer 실패 후 롤백된 경우 skip
                if (couponRedisRepository.isAlreadyIssued(event.couponId().toString(), event.userId().toString())) {
                    validEvents.add(event);
                } else {
                    log.info("[CouponIssueRequestedConsumer] Redis 발급 기록 없음 — 롤백된 요청으로 skip. couponId={}, userId={}",
                            event.couponId(), event.userId());
                }
            } catch (Exception e) {
                log.error("[CouponIssueRequestedConsumer] 메시지 파싱 또는 Redis 검증 실패. message={}", message, e);
            }
        }

        if (!validEvents.isEmpty()) {
            try {
                // 초고속 배치 쓰기 시도 (Bulk Insert)
                writer.writeBatch(validEvents);
            } catch (Exception e) {
                // 가용성 최우선 Fallback: 배치 처리 중 예외 발생 시, 
                // 단건으로 쪼개서 하나씩 저장 시도 (성공한 것은 남고, 중복은 UNIQUE 제약 조건에 의해 안전하게 무시)
                log.warn("[CouponIssueRequestedConsumer] 배치 저장 중 예외 발생 - 단건 순차 처리로 Fallback 진행. error={}", e.getMessage());
                for (CouponIssueRequestedEvent event : validEvents) {
                    try {
                        writer.write(event);
                    } catch (DataIntegrityViolationException dive) {
                        log.warn("[CouponIssueRequestedConsumer] Fallback 단건 처리 - DB UNIQUE 위반 skip. couponId={}, userId={}", 
                                event.couponId(), event.userId());
                    } catch (Exception ex) {
                        log.error("[CouponIssueRequestedConsumer] Fallback 단건 처리 실패. couponId={}, userId={}", 
                                event.couponId(), event.userId(), ex);
                    }
                }
            }
        }

        acknowledgment.acknowledge();
    }
}
