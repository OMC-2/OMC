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
public class RefundDoneConsumer {

    private final CouponSagaService couponSagaService;

    /**
     * refund.done: 환불 완료 이벤트.
     * refundReason == STOCK_DEDUCT_FAILED 일 때만 쿠폰 복구 (Case B).
     * USER_CANCEL / RAFFLE_LOSE 는 쿠폰 소멸 (no-op).
     */
    @KafkaListener(topics = KafkaTopics.REFUND_DONE, groupId = "coupon-service")
    public void handle(
            @Payload Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        String eventId = String.valueOf(event.get("eventId"));
        log.info("[RefundDoneConsumer] 수신. topic={}, offset={}, eventId={}", topic, offset, eventId);

        String refundReason = String.valueOf(event.get("refundReason"));
        if (!"STOCK_DEDUCT_FAILED".equals(refundReason)) {
            log.info("[RefundDoneConsumer] 쿠폰 소멸 처리 (복구 안 함). reason={}", refundReason);
            return;
        }

        String orderId = String.valueOf(event.get("orderId"));
        couponSagaService.restoreCouponFromUsed(eventId, topic, UUID.fromString(orderId));
    }
}
