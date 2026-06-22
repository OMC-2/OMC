package com.omc.raffle.application.scheduler;

import com.omc.raffle.application.port.out.EventProducerPort;
import com.omc.raffle.domain.entity.OutboxEvent;
import com.omc.raffle.domain.enums.OutboxStatus;
import com.omc.raffle.domain.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
                eventProducerPort.send(event.getEventType(), event.getAggregateId(), event.getPayload())
                        .thenAccept(success -> {
                            if (Boolean.TRUE.equals(success)) {
                                event.markAsPublished();
                                log.info("OutboxEvent {} published successfully", event.getId());
                            } else {
                                event.markAsFailed();
                                log.error("OutboxEvent {} failed to publish", event.getId());
                            }
                            // Note: 실제 운영 환경에서는 비동기 콜백에서 엔티티 상태를 직접 바꾸는 것보다,
                            // 별도의 트랜잭션을 열어 상태를 업데이트하거나, 동기적으로 결과를 기다리는 방식을 선택할 수 있습니다.
                            // 여기서는 단순함을 위해 트랜잭션 범위 내(비동기 스레드 실행 시점 주의 필요)로 유지.
                        }).join(); // 동기 대기로 확실하게 상태 업데이트 보장
                
            } catch (Exception e) {
                log.error("Exception occurred while publishing outbox event {}", event.getId(), e);
                event.markAsFailed();
            }
        }
    }
}
