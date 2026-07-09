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
public class DropOpenedConsumer {

    private final NotificationService notificationService;
    private final ProcessedEventIdempotencyService processedEventIdempotencyService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.DROP_OPENED, groupId = "notification-service")
    public void handle(List<ConsumerRecord<String, String>> records, Acknowledgment acknowledgment) {
        record DropEvent(String eventId, UUID dropId, List<UUID> userIds) {}

        List<DropEvent> parsed = new ArrayList<>(records.size());
        for (int i = 0; i < records.size(); i++) {
            ConsumerRecord<String, String> record = records.get(i);
            try {
                Map<String, Object> event = parseEvent(record.value(), record.topic());
                String eventId = String.valueOf(event.get("eventId"));
                log.info("[DropOpenedConsumer] 수신. topic={}, offset={}, eventId={}", record.topic(), record.offset(), eventId);
                UUID dropId = UUID.fromString(String.valueOf(event.get("dropId")));
                Object userIdsObj = event.get("userIds");
                if (!(userIdsObj instanceof List<?> rawIds)) {
                    log.warn("[DropOpenedConsumer] userIds 없음. eventId={}", eventId);
                    parsed.add(new DropEvent(eventId, dropId, List.of()));
                } else {
                    List<UUID> userIds = rawIds.stream()
                            .map(uid -> UUID.fromString(String.valueOf(uid)))
                            .toList();
                    parsed.add(new DropEvent(eventId, dropId, userIds));
                }
            } catch (Exception e) {
                throw new BatchListenerFailedException("레코드 처리 실패", e, i);
            }
        }
        if (parsed.isEmpty()) { acknowledgment.acknowledge(); return; }

        List<String> compositeKeys = parsed.stream()
                .flatMap(pe -> pe.userIds().stream().map(uid -> pe.eventId() + ":" + uid))
                .toList();

        Set<String> duplicates = compositeKeys.isEmpty()
                ? Set.of()
                : processedEventIdempotencyService.filterAndMarkProcessed(compositeKeys, records.get(0).topic());

        List<Notification> toSave = new ArrayList<>();
        for (DropEvent pe : parsed) {
            for (UUID userId : pe.userIds()) {
                String key = pe.eventId() + ":" + userId;
                if (duplicates.contains(key)) {
                    log.debug("[DropOpenedConsumer] 중복 이벤트 스킵. key={}", key);
                    continue;
                }
                toSave.add(Notification.createPending(userId, NotificationType.DROP_OPENED,
                        "드롭 오픈 알림", "선착순 드롭이 시작되었습니다!", pe.dropId(), "DROP"));
            }
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
