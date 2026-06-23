package com.omc.coupon.application.service;

import com.omc.coupon.domain.entity.OutboxEvent;
import com.omc.coupon.domain.enums.OutboxStatus;
import com.omc.coupon.domain.repository.OutboxEventRepository;
import com.omc.coupon.infrastructure.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private static final int MAX_RETRY = 3;

    private static final Map<String, String> TOPIC_MAP = Map.of(
            "COUPON_ISSUED", KafkaTopics.COUPON_ISSUED,
            "COUPON_USED",   KafkaTopics.COUPON_USED
    );

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository
                .findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.INIT);

        for (OutboxEvent event : pending) {
            String topic = TOPIC_MAP.get(event.getEventType().name());
            if (topic == null) {
                log.warn("[OutboxService] 알 수 없는 event_type={}", event.getEventType());
                continue;
            }

            try {
                kafkaTemplate.send(topic, event.getAggregateId().toString(), event.getPayload()).get();
                event.publish();
                log.debug("[OutboxService] 발행 완료. eventId={}, topic={}", event.getEventId(), topic);
            } catch (Exception e) {
                event.incrementRetry(MAX_RETRY);
                log.error("[OutboxService] 발행 실패. eventId={}, retryCount={}, error={}",
                        event.getEventId(), event.getRetryCount(), e.getMessage());
            }
        }
    }
}
