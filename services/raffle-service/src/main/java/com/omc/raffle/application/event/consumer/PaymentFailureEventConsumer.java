package com.omc.raffle.application.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.raffle.application.service.RaffleDrawService;
import com.omc.raffle.domain.entity.ProcessedEvent;
import com.omc.raffle.domain.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFailureEventConsumer {

    private static final String TOPIC = "payment.failed";

    private final ObjectMapper objectMapper;
    private final RaffleDrawService raffleDrawService;
    private final ProcessedEventRepository processedEventRepository;

    /**
     * [멱등성 검증] 이미 처리된 event_id이면 true를 반환합니다.
     *
     * 리스너 트랜잭션 안에서 호출되므로, 비즈니스 로직이 롤백되면
     * ProcessedEvent 저장도 함께 롤백됩니다 → 재시도 시 재처리 보장.
     */
    private boolean isAlreadyProcessed(String eventId) {
        if (processedEventRepository.existsById(eventId)) {
            log.warn("[Idempotency] 이미 처리된 이벤트입니다. 무시합니다. eventId={}, topic={}", eventId, TOPIC);
            return true;
        }
        processedEventRepository.save(ProcessedEvent.create(eventId, TOPIC));
        return false;
    }

    @KafkaListener(topics = TOPIC, groupId = "raffle-service-group")
    @Transactional
    public void consumePaymentFailedEvent(String message) {
        PaymentFailedRequest event;
        try {
            event = objectMapper.readValue(message, PaymentFailedRequest.class);
        } catch (Exception e) {
            throw new MessageConversionException("이벤트 역직렬화 실패: topic=" + TOPIC, e);
        }
        if (!"RAFFLE".equalsIgnoreCase(event.salesType())) {
            log.debug("Ignored payment.failed event for salesType: {}", event.salesType());
            return;
        }
        if (isAlreadyProcessed(event.eventId())) return;
        raffleDrawService.handlePaymentFailure(event.raffleId(), event.userId());
    }
}
