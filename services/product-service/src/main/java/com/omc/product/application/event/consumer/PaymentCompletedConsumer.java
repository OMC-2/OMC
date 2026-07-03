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

        // confirmDeduct() 성공 시 컨테이너가 offset을 자동 커밋하고,
        // 예외 발생 시에는 DefaultErrorHandler가 재시도/DLT 처리를 담당한다 (수동 ack 없음)
        inventoryService.confirmDeduct(event);
    }
}