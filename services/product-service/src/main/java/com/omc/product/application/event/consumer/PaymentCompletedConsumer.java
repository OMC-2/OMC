package com.omc.product.application.event.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.product.application.event.PaymentCompletedEvent;
import com.omc.product.application.service.InventoryService;
import com.omc.product.domain.exception.PoisonMessageException;
import com.omc.product.infrastructure.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * payment.completed 이벤트 컨슈머
 *
 * concurrency는 토픽 파티션 수(3)에 맞춰 3으로 설정
 * InventoryService.confirmDeduct()에 Semaphore 기반 동시성 제한이 걸려있음
 * 이 값을 올릴 때는 InventoryService 설정과 맞춰서 같이 검토
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCompletedConsumer {

    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = KafkaTopics.PAYMENT_COMPLETED,
            groupId = "product-service",
            concurrency = "3"
    )
    public void handlePaymentCompleted(
            String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.info("[PaymentCompletedConsumer] 수신. topic={}, offset={}", topic, offset);

        PaymentCompletedEvent event;
        try {
            event = objectMapper.readValue(message, PaymentCompletedEvent.class);
            log.info("[PaymentCompletedConsumer] 역직렬화 완료. eventId={}", event.eventId());
        } catch (JsonProcessingException e) {
            log.error("[PaymentCompletedConsumer] 역직렬화 실패. message={}, error={}",
                    message, e.getMessage());
            throw new PoisonMessageException("역직렬화 실패: " + e.getMessage(), e);
        }

        // 성공/실패 처리와 offset 커밋은 InventoryService.confirmDeduct() 내부에서 처리
        inventoryService.confirmDeduct(event);
    }
}