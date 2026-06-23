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
public class RefundDoneConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = KafkaTopics.REFUND_DONE, groupId = "notification-service")
    public void handle(
            @Payload Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        String eventId = String.valueOf(event.get("eventId"));
        log.info("[RefundDoneConsumer] 수신. topic={}, offset={}, eventId={}", topic, offset, eventId);

        UUID userId = UUID.fromString(String.valueOf(event.get("userId")));
        UUID orderId = UUID.fromString(String.valueOf(event.get("orderId")));
        Object amount = event.get("amount");

        notificationService.send(eventId, topic, userId,
                NotificationType.REFUND_COMPLETED,
                "환불 완료 알림",
                "환불이 완료되었습니다." + (amount != null ? " 환불 금액: " + amount + "원" : ""),
                orderId, "ORDER");
    }
}
