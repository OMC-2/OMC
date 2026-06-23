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

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RaffleLoserConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = KafkaTopics.RAFFLE_LOSER, groupId = "notification-service")
    public void handle(
            @Payload Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        String eventId = String.valueOf(event.get("eventId"));
        log.info("[RaffleLoserConsumer] 수신. topic={}, offset={}, eventId={}", topic, offset, eventId);

        UUID userId = UUID.fromString(String.valueOf(event.get("userId")));
        UUID raffleId = UUID.fromString(String.valueOf(event.get("raffleId")));

        notificationService.send(eventId, topic, userId,
                NotificationType.RAFFLE_LOSE,
                "래플 낙첨 알림",
                "아쉽게도 이번 래플에 당첨되지 않았습니다. 다음 기회를 노려보세요!",
                raffleId, "RAFFLE");
    }
}
