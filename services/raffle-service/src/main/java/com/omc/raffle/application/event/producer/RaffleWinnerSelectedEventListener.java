package com.omc.raffle.application.event.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.raffle.domain.entity.OutboxEvent;
import com.omc.raffle.domain.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class RaffleWinnerSelectedEventListener {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleRaffleWinnerSelectedEvent(RaffleWinnerSelectedEvent event) {
        log.info("Saving OutboxEvent for RaffleWinnerSelectedEvent: {}", event.raffleId());

        try {
            String payload = objectMapper.writeValueAsString(event);

            OutboxEvent outboxEvent = OutboxEvent.create(
                    event.raffleId().toString(),
                    "Raffle",
                    "raffle.winner.selected",
                    payload
            );

            outboxEventRepository.save(outboxEvent);
            log.info("Successfully saved OutboxEvent. Event ID: {}", outboxEvent.getId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize RaffleWinnerSelectedEvent", e);
            throw new RuntimeException("Failed to serialize Outbox payload", e);
        }
    }
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleRaffleLoserNotifiedEvent(RaffleLoserNotifiedEvent event) {
        log.info("Saving OutboxEvent for RaffleLoserNotifiedEvent: raffleId={}, userId={}", event.raffleId(), event.userId());

        try {
            String payload = objectMapper.writeValueAsString(event);

            OutboxEvent outboxEvent = OutboxEvent.create(
                    event.raffleId().toString(), // aggregateId
                    "Raffle",
                    "raffle.loser.notified",
                    payload
            );

            outboxEventRepository.save(outboxEvent);
            log.info("Successfully saved OutboxEvent for loser. Event ID: {}", outboxEvent.getId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize RaffleLoserNotifiedEvent", e);
            throw new RuntimeException("Failed to serialize Outbox payload", e);
        }
    }
}
