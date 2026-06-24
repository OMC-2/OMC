package com.omc.order.application.scheduler;

import com.omc.order.domain.entity.OrderOutboxEvent;
import com.omc.order.domain.enums.OutboxStatus;
import com.omc.order.domain.repository.OrderOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderOutboxPoller {

  private static final int BATCH_SIZE = 100;
  private static final int MAX_RETRY = 5;
  private static final long KAFKA_ACK_TIMEOUT_MS = 5_000L;

  private final OrderOutboxRepository outboxRepository;
  private final KafkaTemplate<String, String> kafkaTemplate;

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
        //동일 주문 이벤트의 파티션 순서 보장
        kafkaTemplate
            .send(event.getTopic(), event.getAggregateId().toString(), event.getPayload())
            .get(KAFKA_ACK_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        event.markPublished();
        log.info("[OutboxPoller] 발행 성공: eventType={}, topic={}, eventId={}", event.getEventType(), event.getTopic(), event.getEventId());

      } catch (Exception e) {
        event.increaseRetry();
        log.error("[OutboxPoller]발행 실패(retry={}): eventType={}, eventId={}", event.getRetryCount(), event.getEventType(), event.getEventId(), e);

        if(event.getRetryCount() >= MAX_RETRY) {
          event.markFailed();
          log.error("[OutboxPoller] 최대 재시도 초과 -> FAILED 격리: eventId={}", event.getEventId());
        }
        //INIT 유지 시 다음 주기에 재시도. (트랜잭션 커밋으로 retryCount/status 반영)
      }
    }
  }
}
