package com.omc.notification.application.event.consumer;

import com.omc.notification.application.service.NotificationService;
import com.omc.notification.domain.enums.NotificationType;
import com.omc.notification.infrastructure.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DropOpenedConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = KafkaTopics.DROP_OPENED, groupId = "notification-service")
    public void handle(
            @Payload Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        String eventId = String.valueOf(event.get("eventId"));
        log.info("[DropOpenedConsumer] 수신. topic={}, offset={}, eventId={}", topic, offset, eventId);

        UUID dropId = UUID.fromString(String.valueOf(event.get("dropId")));

        // drop.opened는 특정 유저가 아닌 전체 알림 → 수신 대상 목록이 필요할 경우 확장
        // 현재는 이벤트 페이로드에 포함된 userId 목록 기반으로 처리
        Object userIdsObj = event.get("userIds");
        if (!(userIdsObj instanceof List<?> userIds)) {
            log.warn("[DropOpenedConsumer] userIds 없음. eventId={}", eventId);
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
    }
}
