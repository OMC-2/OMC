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
public class RaffleWinnerConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = KafkaTopics.RAFFLE_WINNER, groupId = "notification-service")
    public void handle(
            @Payload Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        String eventId = String.valueOf(event.get("eventId"));
        log.info("[RaffleWinnerConsumer] 수신. topic={}, offset={}, eventId={}", topic, offset, eventId);

        UUID userId = UUID.fromString(String.valueOf(event.get("userId")));
        UUID raffleId = UUID.fromString(String.valueOf(event.get("raffleId")));

        notificationService.send(eventId, topic, userId,
                NotificationType.RAFFLE_WIN,
                "래플 당첨 알림",
                "축하합니다! 래플에 당첨되었습니다. 결제를 진행해주세요.",
                raffleId, "RAFFLE");
    }
}
