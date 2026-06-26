package com.omc.order.application.scheduler;

import com.omc.order.domain.entity.OrderOutboxEvent;
import com.omc.order.domain.enums.OutboxStatus;
import com.omc.order.domain.repository.OrderOutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

//OrderOutboxPoller 단위 테스트
//핵심검증
//발행 성공 시 INIT -> PUBLISHED
//발행 실패 시 retryCount 증가, 상태 유지(INIT) -> 다음 주기 재시도
//MAX_RETRY 초과 시 FAILED 격리

@ExtendWith(MockitoExtension.class)
class OrderOutboxPollerTest {

  @Mock OrderOutboxRepository outboxRepository;
  @Mock
  KafkaTemplate<String, String> kafkaTemplate;

  @InjectMocks OrderOutboxPoller poller;

  private OrderOutboxEvent initEvent() {
    return OrderOutboxEvent.create(
        UUID.randomUUID(), "ORDER", UUID.randomUUID(),
        "ORDER_CREATED", "order.created", "{\"k\":\"v\"}");
  }

  // retryCount를 직접 세팅하기 위한 리플렉션 헬퍼 (테스트 전용)
  private void setRetryCount(OrderOutboxEvent event, int count) throws Exception {
    Field f = OrderOutboxEvent.class.getDeclaredField("retryCount");
    f.setAccessible(true);
    f.setInt(event, count);
  }

  @Test
  @DisplayName("발행 성공: INIT -> PUBLISHED, publishedAt 기록")
  void publishSuccess() {
    OrderOutboxEvent event = initEvent();
    when(outboxRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.INIT), any(Pageable.class)))
        .thenReturn(List.of(event));
    when(kafkaTemplate.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(mockSendResult()));

    poller.publishPending();

    assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    assertThat(event.getPublishedAt()).isNotNull();
  }

  @Test
  @DisplayName("발행 실패: retryCount 증가, 상태는 INIT 유지 (다음 주기 재시도)")
  void publishFailureKeepsInit() {
    OrderOutboxEvent event = initEvent();
    when(outboxRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.INIT), any(Pageable.class)))
        .thenReturn(List.of(event));
    CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
    failed.completeExceptionally(new RuntimeException("kafka down"));
    when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failed);

    poller.publishPending();

    assertThat(event.getRetryCount()).isEqualTo(1);
    assertThat(event.getStatus()).isEqualTo(OutboxStatus.INIT); // 아직 FAILED 아님
  }

  @Test
  @DisplayName("MAX_RETRY(5) 초과: FAILED로 격리")
  void maxRetryIsolatesToFailed() throws Exception {
    OrderOutboxEvent event = initEvent();
    setRetryCount(event, 4); // 이번 실패로 5가 되어 임계 도달
    when(outboxRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.INIT), any(Pageable.class)))
        .thenReturn(List.of(event));
    CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
    failed.completeExceptionally(new RuntimeException("kafka down"));
    when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failed);

    poller.publishPending();

    assertThat(event.getRetryCount()).isEqualTo(5);
    assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
  }

  @Test
  @DisplayName("발행 대기 건이 없으면 Kafka를 호출하지 않는다")
  void noPendingNoKafkaCall() {
    when(outboxRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.INIT), any(Pageable.class)))
        .thenReturn(List.of());

    poller.publishPending();

    verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
  }

  @SuppressWarnings("unchecked")
  private SendResult<String, String> mockSendResult() {
    return org.mockito.Mockito.mock(SendResult.class);
  }
}
