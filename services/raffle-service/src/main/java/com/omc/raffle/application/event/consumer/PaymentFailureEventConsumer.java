package com.omc.raffle.application.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.raffle.application.service.RaffleDrawService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFailureEventConsumer {

    private final ObjectMapper objectMapper;
    private final RaffleDrawService raffleDrawService;

    @KafkaListener(topics = "payment.failed", groupId = "raffle-service-group")
    public void consumePaymentFailedEvent(String message) {
        try {
            PaymentFailedRequest event = objectMapper.readValue(message, PaymentFailedRequest.class);
            log.info("Received payment.failed event: {}", event);

            // 래플 서비스에서 보낸 결제건만 처리
            if ("RAFFLE".equalsIgnoreCase(event.salesType())) {
                log.info("Processing RAFFLE payment failure. raffleId={}, userId={}", event.raffleId(), event.userId());
                
                // eventId가 아니라 raffleId를 추출하여 Raffle 도메인에 전달
                raffleDrawService.handlePaymentFailure(event.raffleId(), event.userId());
            } else {
                log.debug("Ignored payment.failed event for salesType: {}", event.salesType());
            }
        } catch (Exception e) {
            log.error("Error processing payment.failed event", e);
            throw new com.omc.raffle.domain.exception.EventProcessingException(com.omc.raffle.domain.exception.RaffleErrorCode.RAFFLE_009, e.getMessage());
        }
    }
}
