package com.omc.raffle.application.scheduler;

import com.omc.raffle.application.port.out.EventProducerPort;
import com.omc.raffle.domain.entity.OutboxEvent;
import com.omc.raffle.domain.enums.OutboxStatus;
import com.omc.raffle.domain.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPollerScheduler {

    private final OutboxEventRepository outboxEventRepository;
    private final EventProducerPort eventProducerPort;

    @Scheduled(fixedDelay = 5000) // 5초마다 실행
    @SchedulerLock(name = "pollAndPublishOutboxEvents", lockAtLeastFor = "PT4S", lockAtMostFor = "PT10S")
    @Transactional
    public void pollAndPublishOutboxEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findAllByStatusIn(
                List.of(OutboxStatus.INIT, OutboxStatus.FAILED)
        );

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("Found {} pending outbox events to publish", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                // 키는 aggregateId (raffleId) 로 설정하여 파티션 순서 보장
                boolean success = eventProducerPort.send(event.getEventType(), event.getAggregateId(), event.getPayload());
                if (success) {
                    event.markAsPublished();
                    log.info("OutboxEvent {} published successfully", event.getId());
                } else {
                    event.markAsFailed();
                    if (event.getStatus() == OutboxStatus.DEAD) {
                        log.error("OutboxEvent {} reached DEAD status after max retries", event.getId());
                    } else {
                        log.warn("OutboxEvent {} failed to publish, will retry", event.getId());
                    }
                }
            } catch (Exception e) {
                log.error("Exception occurred while publishing outbox event {}", event.getId(), e);
                event.markAsFailed();
                if (event.getStatus() == OutboxStatus.DEAD) {
                    log.error("OutboxEvent {} reached DEAD status after max retries due to exception", event.getId());
                }
            }
        }
    }
}
