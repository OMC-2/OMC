package com.omc.payment.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.payment.application.event.producer.PaymentCompletedEvent;
import com.omc.payment.application.event.producer.PaymentFailedEvent;
import com.omc.payment.application.event.producer.RefundDoneEvent;
import com.omc.payment.domain.entity.PaymentOutboxEvent;
import com.omc.payment.domain.enums.OutboxEventStatus;
import com.omc.payment.domain.repository.PaymentOutboxEventRepository;
import com.omc.payment.infrastructure.config.KafkaTopics;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOutboxPublishProcessor {

    private final PaymentOutboxEventRepository paymentOutboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /*
    * Bulk로 읽은 Outbox 데이터들을 하나씩 발행
    * */
    @Transactional
    public void publish(UUID eventId) {
        // 중복 발행 방지
        PaymentOutboxEvent outboxEvent = paymentOutboxEventRepository.findById(eventId)
                .orElse(null);

        if (outboxEvent == null || outboxEvent.getStatus() == OutboxEventStatus.PUBLISHED) {
            return;
        }

        try {
            Object payload = deserializePayload(outboxEvent);
            kafkaTemplate.send(
                    outboxEvent.getEventType(),
                    outboxEvent.getAggregateId().toString(),
                    payload
            ).get(); // 동기 대기

            outboxEvent.markPublished();
        } catch (InterruptedException e) { // Future.get() 과정에서 스레드 인터럽트 예외
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            outboxEvent.markFailed();
            log.warn("결제 아웃박스 이벤트 payload 역직렬화 또는 발행에 실패했습니다. eventId={}", eventId, e);
        }
    }

    private Object deserializePayload(PaymentOutboxEvent outboxEvent) throws Exception {
        return switch (outboxEvent.getEventType()) {
            case KafkaTopics.PAYMENT_COMPLETED ->
                objectMapper.readValue(outboxEvent.getPayload(), PaymentCompletedEvent.class);
            case KafkaTopics.PAYMENT_FAILED ->
                objectMapper.readValue(outboxEvent.getPayload(), PaymentFailedEvent.class);
            case KafkaTopics.REFUND_DONE ->
                objectMapper.readValue(outboxEvent.getPayload(), RefundDoneEvent.class);
            default -> throw new IllegalStateException("존재하지 않는 아웃박스 이벤트 타입입니다. eventType=" + outboxEvent.getEventType());
        };
    }
}
