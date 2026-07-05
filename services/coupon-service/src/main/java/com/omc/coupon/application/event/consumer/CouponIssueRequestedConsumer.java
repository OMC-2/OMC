package com.omc.coupon.application.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.coupon.application.event.dto.inbound.CouponIssueRequestedEvent;
import com.omc.coupon.application.service.CouponIssueWriterService;
import com.omc.coupon.infrastructure.kafka.KafkaTopics;
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
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.COUPON_ISSUE_REQUESTED, groupId = "coupon-service")
    public void handle(
            List<ConsumerRecord<String, String>> records,
            Acknowledgment acknowledgment
    ) {
        List<CouponIssueRequestedEvent> validEvents = new ArrayList<>();

        for (ConsumerRecord<String, String> record : records) {
            String message = record.value();
            try {
                CouponIssueRequestedEvent event = objectMapper.readValue(message, CouponIssueRequestedEvent.class);
                validEvents.add(event);
            } catch (Exception e) {
                log.error("[CouponIssueRequestedConsumer] 메시지 파싱 실패. message={}", message, e);
            }
        }

        if (!validEvents.isEmpty()) {
            try {
                writer.writeBatch(validEvents);
            } catch (Exception e) {
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
