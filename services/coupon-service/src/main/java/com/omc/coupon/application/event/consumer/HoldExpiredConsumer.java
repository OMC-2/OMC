package com.omc.coupon.application.event.consumer;

import com.omc.coupon.application.service.CouponSagaService;
import com.omc.coupon.infrastructure.kafka.KafkaTopics;
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
public class HoldExpiredConsumer {

    private final CouponSagaService couponSagaService;

    /**
     * hold.expired: 드롭 재고 선점 TTL 만료 시 발행.
     * 결제 버튼을 누르지 않은 경우 RESERVED 쿠폰이 없으므로 no-op.
     * 결제 버튼을 눌렀다가 10분 경과한 경우 RESERVED → AVAILABLE 복구.
     */
    @KafkaListener(topics = KafkaTopics.HOLD_EXPIRED, groupId = "coupon-service")
    public void handle(
            @Payload Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        String eventId = String.valueOf(event.get("eventId"));
        log.info("[HoldExpiredConsumer] 수신. topic={}, offset={}, eventId={}", topic, offset, eventId);

        String orderId = String.valueOf(event.get("orderId"));
        couponSagaService.restoreCoupon(eventId, topic, UUID.fromString(orderId));
    }
}
