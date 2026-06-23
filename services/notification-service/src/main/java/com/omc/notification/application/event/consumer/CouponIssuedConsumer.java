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
public class CouponIssuedConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = KafkaTopics.COUPON_ISSUED, groupId = "notification-service")
    public void handle(
            @Payload Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        String eventId = String.valueOf(event.get("eventId"));
        log.info("[CouponIssuedConsumer] 수신. topic={}, offset={}, eventId={}", topic, offset, eventId);

        UUID userId = UUID.fromString(String.valueOf(event.get("userId")));
        UUID couponId = UUID.fromString(String.valueOf(event.get("couponId")));

        notificationService.send(eventId, topic, userId,
                NotificationType.COUPON_ISSUED,
                "쿠폰 발급 알림",
                "쿠폰이 발급되었습니다. 결제 시 사용해보세요!",
                couponId, "COUPON");
    }
}
