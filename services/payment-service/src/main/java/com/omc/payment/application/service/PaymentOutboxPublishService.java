package com.omc.payment.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.payment.domain.entity.PaymentOutboxEvent;
import com.omc.payment.domain.enums.OutboxEventStatus;
import com.omc.payment.domain.repository.PaymentOutboxEventRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOutboxPublishService {

    private final PaymentOutboxEventRepository paymentOutboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /*
    * Bulk로 읽은 Outbox 데이터들을 하나씩 발행
    * 스케줄러는 트랜잭션에 포함되지 않기 때문에 객체가 아닌 eventId를 인자로 전달
    * */
    @Transactional
    public void publish(UUID eventId, int maxRetryCount) {
        // 중복 발행 방지를 위한 상태 선점
        // 다중 인스턴스를 고려하여 DB 원자적 조건 업데이트 수행
        int claimed = paymentOutboxEventRepository.claimForPublishing(
                eventId,
                OutboxEventStatus.PUBLISHING,
                List.of(OutboxEventStatus.INIT, OutboxEventStatus.FAILED),
                maxRetryCount
        );
        // 이미 선점 중
        if (claimed == 0) {
            return;
        }

        PaymentOutboxEvent outboxEvent = paymentOutboxEventRepository.findById(eventId)
                .orElse(null);

        if (outboxEvent == null) {
            return;
        }

        try {
            kafkaTemplate.send(
                    outboxEvent.getEventType(),
                    outboxEvent.getAggregateId().toString(),
                    outboxEvent.getPayload()
            ).get(); // 동기 대기

            outboxEvent.markPublished();
        } catch (InterruptedException e) { // Future.get() 과정에서 스레드 인터럽트 예외
            outboxEvent.markFailed();
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            outboxEvent.markFailed();
            log.warn("결제 아웃박스 이벤트 payload 역직렬화 또는 발행에 실패했습니다. eventId={}", eventId, e);
        }
    }
}
