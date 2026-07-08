package com.omc.coupon.application.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.coupon.application.event.dto.inbound.RefundDoneEvent;
import com.omc.coupon.application.service.CouponSagaService;
import com.omc.coupon.infrastructure.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefundDoneConsumer {

    private final CouponSagaService couponSagaService;
    private final ObjectMapper objectMapper;

    // refund.done: STOCK_DEDUCT_FAILED 일 때만 USED → AVAILABLE 복구. 그 외 reason은 no-op.
    @KafkaListener(topics = KafkaTopics.REFUND_DONE, groupId = "coupon-service")
    public void handle(
            String message,
            Acknowledgment acknowledgment,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        RefundDoneEvent event = readValue(message, RefundDoneEvent.class, topic);

        if (!"STOCK_DEDUCT_FAILED".equals(event.refundReason())) {
            acknowledgment.acknowledge();
            return;
        }

        couponSagaService.restoreCouponFromUsed(event.eventId(), topic, event.orderId());
        acknowledgment.acknowledge();
    }

    private <T> T readValue(String message, Class<T> targetType, String topic) {
        try {
            return objectMapper.readValue(message, targetType);
        } catch (Exception e) {
            log.error("{} 이벤트 역직렬화 실패. payload={}", topic, message, e);
            throw new IllegalStateException(topic + " 이벤트 역직렬화 실패", e);
        }
    }
}
