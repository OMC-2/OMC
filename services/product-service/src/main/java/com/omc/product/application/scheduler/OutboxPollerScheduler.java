package com.omc.product.application.scheduler;

import com.omc.product.domain.entity.OutboxEvent;
import com.omc.product.domain.enums.OutboxStatus;
import com.omc.product.domain.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPollerScheduler {

    private static final int MAX_RETRY = 3;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    // event_type → Kafka 토픽명 매핑
    private static final Map<String, String> TOPIC_MAP = Map.of(
            "STOCK_DEDUCTED", "stock.deducted",
            "STOCK_FAILED",   "stock.failed"
    );

    @Scheduled(fixedDelay = 500)  // 0.5초 주기
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository
                .findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.INIT);

        for (OutboxEvent event : pending) {
            String topic = TOPIC_MAP.getOrDefault(event.getEventType(), null);
            if (topic == null) {
                log.warn("[OutboxPoller] 알 수 없는 event_type={}", event.getEventType());
                continue;
            }

            try {
                kafkaTemplate.send(topic, event.getAggregateId().toString(), event.getPayload())
                        .get(); // 동기 ACK 대기
                event.publish();
                log.debug("[OutboxPoller] 발행 완료. eventId={}, topic={}", event.getEventId(), topic);
            } catch (Exception e) {
                event.incrementRetry(MAX_RETRY);
                log.error("[OutboxPoller] 발행 실패. eventId={}, retryCount={}, error={}",
                        event.getEventId(), event.getRetryCount(), e.getMessage());
            }
        }
    }
}