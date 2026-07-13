package com.omc.drop.application.scheduler;

import com.omc.drop.domain.entity.DropOutboxEvent;
import com.omc.drop.domain.repository.DropOutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class DropOutboxPoller {

    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRY = 5;
    private static final long KAFKA_ACK_TIMEOUT_MS = 5_000L;

    private final DropOutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private final Timer publishTimer;
    private final Timer publishLagTimer;
    private final Counter publishSuccess;
    private final Counter publishFailure;
    private final Counter publishDlq;

    public DropOutboxPoller(DropOutboxEventRepository outboxRepository,
                             KafkaTemplate<String, String> kafkaTemplate,
                             MeterRegistry meterRegistry) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;

        this.publishTimer = Timer.builder("drop.outbox.publish.duration")
                .description("아웃박스 이벤트 1건의 Kafka 발행 소요시간")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        this.publishLagTimer = Timer.builder("drop.outbox.publish.lag")
                .description("이벤트 생성 시각부터 발행 완료까지의 지연")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        this.publishSuccess = Counter.builder("drop.outbox.publish.success")
                .description("아웃박스 발행 성공 누적 건수")
                .register(meterRegistry);

        this.publishFailure = Counter.builder("drop.outbox.publish.failure")
                .description("아웃박스 발행 실패(재시도 유발) 누적 건수")
                .register(meterRegistry);

        this.publishDlq = Counter.builder("drop.outbox.publish.dlq")
                .description("최대 재시도 초과로 FAILED 격리된 누적 건수")
                .register(meterRegistry);
    }

    @Transactional
    @Scheduled(fixedRate = 2_000L)
    public void publishPending() {
        List<DropOutboxEvent> pending = outboxRepository.findPendingWithLock(BATCH_SIZE);

        if (pending.isEmpty()) return;

        log.info("[DropOutboxPoller] 발행 대기 {}건 처리 시작", pending.size());

        for (DropOutboxEvent event : pending) {
            long startNs = System.nanoTime();
            try {
                kafkaTemplate
                        .send(event.getTopic(), event.getAggregateId().toString(), event.getPayload())
                        .get(KAFKA_ACK_TIMEOUT_MS, TimeUnit.MILLISECONDS);

                publishTimer.record(System.nanoTime() - startNs, TimeUnit.NANOSECONDS);
                event.markPublished();

                Duration lag = Duration.between(event.getCreatedAt(), LocalDateTime.now());
                if (!lag.isNegative()) publishLagTimer.record(lag);

                publishSuccess.increment();
                log.info("[DropOutboxPoller] 발행 성공: eventId={}, eventType={}, topic={}",
                        event.getEventId(), event.getEventType(), event.getTopic());

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("[DropOutboxPoller] 인터럽트 감지, 폴링 중단");
                return;
            } catch (Exception e) {
                publishTimer.record(System.nanoTime() - startNs, TimeUnit.NANOSECONDS);
                event.increaseRetry();
                publishFailure.increment();
                log.error("[DropOutboxPoller] 발행 실패 (retry={}): eventId={}, eventType={}, topic={}",
                        event.getRetryCount(), event.getEventId(), event.getEventType(), event.getTopic(), e);

                if (event.getRetryCount() >= MAX_RETRY) {
                    event.markFailed();
                    publishDlq.increment();
                    log.error("[DropOutboxPoller] 최대 재시도 초과 → FAILED 격리: eventId={}, eventType={}",
                            event.getEventId(), event.getEventType());
                }
            }
        }
    }
}
