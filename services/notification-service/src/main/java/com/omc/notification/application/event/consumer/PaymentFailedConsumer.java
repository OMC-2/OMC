package com.omc.notification.application.event.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.notification.application.service.NotificationService;
import com.omc.notification.application.service.ProcessedEventIdempotencyService;
import com.omc.notification.domain.entity.Notification;
import com.omc.notification.domain.enums.NotificationType;
import com.omc.notification.infrastructure.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFailedConsumer {

    private final NotificationService notificationService;
    private final ProcessedEventIdempotencyService processedEventIdempotencyService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.PAYMENT_FAILED, groupId = "notification-service")
    public void handle(List<ConsumerRecord<String, String>> records, Acknowledgment acknowledgment) {
        List<Map<String, Object>> events = new ArrayList<>(records.size());
        for (int i = 0; i < records.size(); i++) {
            ConsumerRecord<String, String> record = records.get(i);
            try {
                Map<String, Object> event = parseEvent(record.value(), record.topic());
                log.info("[PaymentFailedConsumer] 수신. topic={}, offset={}, eventId={}", record.topic(), record.offset(), event.get("eventId"));
                events.add(event);
            } catch (Exception e) {
                throw new BatchListenerFailedException("레코드 처리 실패", e, i);
            }
        }
        if (events.isEmpty()) { acknowledgment.acknowledge(); return; }

        List<String> eventIds = events.stream().map(e -> String.valueOf(e.get("eventId"))).toList();
        Set<String> duplicates = processedEventIdempotencyService.filterAndMarkProcessed(eventIds, records.get(0).topic());

        List<Notification> toSave = new ArrayList<>();
        for (Map<String, Object> event : events) {
            String eventId = String.valueOf(event.get("eventId"));
            if (duplicates.contains(eventId)) {
                log.debug("[PaymentFailedConsumer] 중복 이벤트 스킵. eventId={}", eventId);
                continue;
            }
            UUID userId = UUID.fromString(String.valueOf(event.get("userId")));
            UUID orderId = UUID.fromString(String.valueOf(event.get("orderId")));
            toSave.add(Notification.createPending(userId, NotificationType.PAYMENT_FAILED,
                    "결제 실패 알림", "결제에 실패했어요. 다시 시도해주세요.", orderId, "ORDER"));
        }
        notificationService.savePendingBatch(toSave);
        acknowledgment.acknowledge();
    }

    private Map<String, Object> parseEvent(String message, String topic) {
        try {
            return objectMapper.readValue(message, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("{} 이벤트 역직렬화 실패. payload={}", topic, message, e);
            throw new IllegalStateException(topic + " 이벤트 역직렬화 실패", e);
        }
    }
}
