package com.omc.order.application.event.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.order.domain.entity.OrderOutboxEvent;
import com.omc.order.domain.enums.OutboxStatus;
import com.omc.order.domain.repository.OrderOutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

//OutboxEventRecorder 단위 테스트
//핵심검증
//record()가 생성한 eventId가 payloadFactory에 그대로 전달됨(payload eventId == outbox PK == 멱등키)
//저장되는 아웃박스 레코드는 INIT 상태이며 PK가 payload에 쓰면 eventId와 동일함
@ExtendWith(MockitoExtension.class)
public class OutboxEventRecorderTest {

  @Mock
  OrderOutboxRepository outboxRepository;
  @Mock
  ObjectMapper objectMapper;

  @InjectMocks
  OutboxEventRecorder recorder;

  @Test
  @DisplayName("record(): 생성된 eventId가 payloadFactory에 전달되고, 동일 eventId로 INIT 적재된다")
  void eventIdIsSharedBetweenPayloadAndOutboxPk() throws Exception {
    AtomicReference<String> capturedEventId = new AtomicReference<>();
    Function<String, Object> payloadFactory = eventId -> {
      capturedEventId.set(eventId);            // factory에 전달된 eventId 캡처
      return new DummyPayload(eventId);
    };
    UUID aggregateId = UUID.randomUUID();

    recorder.record("ORDER", aggregateId, "ORDER_CREATED", "order.created", payloadFactory);

    // 저장된 아웃박스 레코드 캡처
    ArgumentCaptor<OrderOutboxEvent> captor = ArgumentCaptor.forClass(OrderOutboxEvent.class);
    verify(outboxRepository).save(captor.capture());
    OrderOutboxEvent saved = captor.getValue();

    // payload에 들어간 eventId == 아웃박스 PK (멱등성 보장의 핵심)
    assertThat(capturedEventId.get()).isNotNull();
    assertThat(saved.getEventId().toString()).isEqualTo(capturedEventId.get());

    // 적재 시점엔 INIT (아직 발행 전)
    assertThat(saved.getStatus()).isEqualTo(OutboxStatus.INIT);
    assertThat(saved.getTopic()).isEqualTo("order.created");
    assertThat(saved.getEventType()).isEqualTo("ORDER_CREATED");
    assertThat(saved.getAggregateId()).isEqualTo(aggregateId);
    assertThat(saved.getPublishedAt()).isNull();
  }

  record DummyPayload(String eventId) {}
}

