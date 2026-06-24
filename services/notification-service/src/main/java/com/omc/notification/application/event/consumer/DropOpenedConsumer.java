package com.omc.notification.application.event.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.notification.application.service.NotificationService;
import com.omc.notification.domain.enums.NotificationType;
import com.omc.notification.infrastructure.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DropOpenedConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.DROP_OPENED, groupId = "notification-service")
    public void handle(
            String message,
            Acknowledgment acknowledgment,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        Map<String, Object> event = parseEvent(message, topic);
        String eventId = String.valueOf(event.get("eventId"));
        log.info("[DropOpenedConsumer] 수신. topic={}, offset={}, eventId={}", topic, offset, eventId);

        UUID dropId = UUID.fromString(String.valueOf(event.get("dropId")));

        Object userIdsObj = event.get("userIds");
        if (!(userIdsObj instanceof List<?> userIds)) {
            log.warn("[DropOpenedConsumer] userIds 없음. eventId={}", eventId);
            acknowledgment.acknowledge();
            return;
        }

        for (Object uid : userIds) {
            UUID userId = UUID.fromString(String.valueOf(uid));
            notificationService.send(
                    eventId + ":" + userId,
                    topic,
                    userId,
                    NotificationType.DROP_OPENED,
                    "드롭 오픈 알림",
                    "선착순 드롭이 시작되었습니다!",
                    dropId,
                    "DROP"
            );
        }
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
