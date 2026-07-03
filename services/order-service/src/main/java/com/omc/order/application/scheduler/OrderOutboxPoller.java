package com.omc.order.application.scheduler;

import com.omc.order.domain.entity.OrderOutboxEvent;
import com.omc.order.domain.enums.OutboxStatus;
import com.omc.order.domain.repository.OrderOutboxRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
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
public class OrderOutboxPoller {

  private static final int BATCH_SIZE = 100;
  private static final int MAX_RETRY = 5;
  private static final long KAFKA_ACK_TIMEOUT_MS = 5_000L;

  private final OrderOutboxRepository outboxRepository;
  private final KafkaTemplate<String, String> kafkaTemplate;

  //커스텀 메트릭(actuator가 제공하지 않는 아웃박스 발행 지표)
  private final Timer publishTimer; //Kafka 발행 1건 소요시간
  private final Timer publishLagTimer; //이벤트 생성 - 발행 완료까지의 지연(lag)
  private final Counter publishSuccess; //발행 성공 누적
  private final Counter publishFailure; //발행 실패(재시도 유발) 누적
  private final Counter publishDlq; // MAX_RETRY 초과로 FAILED 격리된 누적

  public OrderOutboxPoller(OrderOutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           MeterRegistry meterRegistry) {
    this.outboxRepository = outboxRepository;
    this.kafkaTemplate = kafkaTemplate;

    //메트릭 등록:order.outbox.* 네임스페이스로 Grafana에서 식별
    this.publishTimer =Timer.builder("order.outbox.publish.duration")
        .description("아웃박스 이벤트 1건의 Kafka 발행 소요시간")
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(meterRegistry);

    this.publishLagTimer = Timer.builder("order.outbox.publish.lag")
        .description("이벤트 생성 시각부터 실제 발행까지의 지연(폴러 적체 정도)")
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(meterRegistry);

    this.publishSuccess = Counter.builder("order.outbox.publish.success")
        .description("아웃박스 발행 성공 누적 건수")
        .register(meterRegistry);

    this.publishFailure = Counter.builder("order.outbox.publish.failure")
        .description("아웃박스 발행 실패(재시도 유발) 누적 건수")
        .register(meterRegistry);

    this.publishDlq = Counter.builder("order.outbox.publish.dlq")
        .description("최대 재시도 초과로 FAILED 격리된 누적 건수")
        .register(meterRegistry);
  }

  //2초마다 발행 대기 레코드 처리
  @Scheduled(fixedRate = 2_000L)
  @Transactional
  public void publishPending() {
    List<OrderOutboxEvent> pending = outboxRepository.findByStatusOrderByCreatedAtAsc(
        OutboxStatus.INIT, PageRequest.of(0, BATCH_SIZE));
    if (pending.isEmpty()) {
      return;
    }
    log.info("[OutboxPoller] 발행 대기 {}건 처리 시작", pending.size());

    for (OrderOutboxEvent event : pending) {
      try {
        //발행 소요시간 측정 구간 (Kafka and +ack 대기)
        publishTimer.recordCallable(() -> {
          //동일 주문 이벤트의 파티션 순서 보장
          kafkaTemplate
              .send(event.getTopic(), event.getAggregateId().toString(), event.getPayload())
              .get(KAFKA_ACK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
          return null;
        });

        event.markPublished();

        //발행 지연 기록(lag) : 생성 시각 - 현재시간. 폴러가 밀릴수록 이 값이 커진다.
        Duration lag = Duration.between(event.getCreatedAt(), LocalDateTime.now());
        if (!lag.isNegative()) {
          publishLagTimer.record(lag);
        }
        publishSuccess.increment();
        log.info("[OutboxPoller] 발행 성공: eventType={}, topic={}, eventId={}", event.getEventType(), event.getTopic(), event.getEventId());

      } catch (Exception e) {
        event.increaseRetry();
        publishFailure.increment();
        log.error("[OutboxPoller]발행 실패(retry={}): eventType={}, eventId={}", event.getRetryCount(), event.getEventType(), event.getEventId(), e);

        if(event.getRetryCount() >= MAX_RETRY) {
          event.markFailed();
          publishDlq.increment();
          log.error("[OutboxPoller] 최대 재시도 초과 -> FAILED 격리: eventId={}", event.getEventId());
        }
        //INIT 유지 시 다음 주기에 재시도. (트랜잭션 커밋으로 retryCount/status 반영)
      }
    }
  }
}
