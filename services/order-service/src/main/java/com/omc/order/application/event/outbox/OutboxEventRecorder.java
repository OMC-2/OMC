package com.omc.order.application.event.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.common.util.UuidV7Generator;
import com.omc.order.domain.entity.OrderOutboxEvent;
import com.omc.order.domain.repository.OrderOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventRecorder {

  private final OrderOutboxRepository outboxRepository;
  private final ObjectMapper objectMapper;

  public void record(
      String aggregateType,
      UUID aggregateId,
      String eventType,
      String topic,
      Function<String, Object> payloadFactory
  ) {
    UUID eventId = UuidV7Generator.generate();

    Object payload = payloadFactory.apply(eventId.toString());
    String json = serialize(payload, eventType, aggregateId);

    OrderOutboxEvent outbox = OrderOutboxEvent.create(
        eventId, aggregateType, aggregateId, eventType, topic, json);
    outboxRepository.save(outbox);

    log.info("[Outbox] 적재: eventType={}, topic={}, aggregateId={}, eventId={}", eventType, topic, aggregateId, eventId);
  }

  private String serialize(Object payload, String eventType, UUID aggregateId) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      log.error("[Outbox] 직렬화 실패: eventType={}, aggregateId={}", eventType, aggregateId, e);
      throw new IllegalStateException("아웃박스 페이로드 직렬화 실패: "+ eventType,e);
    }
  }
}
