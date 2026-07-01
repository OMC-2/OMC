package com.omc.order.application.event.consumer;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.omc.order.application.service.OrderService;
import com.omc.order.domain.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.core.NestedExceptionUtils.buildMessage;


/**
 * OrderEventConsumer 멱등성(idempotency) 단위 테스트.

 * 핵심 검증: at-least-once 전송으로 같은 이벤트가 중복 수신되어도
 *           ProcessedEvent(event_id) 로 걸러져 비즈니스 로직은 1번만 실행된다.

 * 검증 방식:
 *  - processedEventRepository.existsById(eventId) 가
 *    1회차에는 false(미처리) -> 2회차에는 true(이미 처리됨) 를 반환하도록 모킹
 *  - 그 결과 orderService.createDropOrder 가 정확히 1번만 호출되는지 verify
 */


@ExtendWith(MockitoExtension.class)
class OrderEventConsumerIdempotencyTest {

  @Mock
  ProcessedEventRepository processedEventRepository;
  @Mock
  OrderService orderService;

  OrderEventConsumer consumer;
  ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    consumer = new OrderEventConsumer(objectMapper, processedEventRepository, orderService);
  }

  @Test
  @DisplayName("동일한 purchase.confirmed 이벤트가 2번 수신되어도 주문 생성은 1번만 실행된다")
  void duplicatePurchaseConfirmed_processedOnlyOnce() {
    //given : 동일한 eventId를 가진 메시지( 중복 전송)
    String eventId = UUID.randomUUID().toString();
    String message = buildMessage(eventId);


    //1회차: 미처리(false), 2회차: 이미 처리됨(true)
    when(processedEventRepository.existsById(eventId))
        .thenReturn(false) //첫 수신
        .thenReturn(true); //중복 수신

    //when: 같은 메시지를 2번 소비
    consumer.consumePurchaseConfirmed(message);
    consumer.consumePurchaseConfirmed(message);

    //then: 멱등성 보장 = createDropOrder는 정확히 1번만 호출
    verify(orderService, times(1)).createDropOrder(any());
    //처리 기록 저장도 1번만 (첫 수신 때만)
    verify(processedEventRepository, times(1)).save(any());
  }

  @Test
  @DisplayName("서로 다른 eventId 이벤트는 각각 독립적으로 처리된다")
  void differentEvents_processedIndependently(){
    //given: 서로 다른 두 이벤트
    String message1 = buildMessage(UUID.randomUUID().toString());
    String message2 = buildMessage(UUID.randomUUID().toString());

    //둘 다 미처리 상태
    when(processedEventRepository.existsById(anyString())).thenReturn(false);

    //when
    consumer.consumePurchaseConfirmed(message1);
    consumer.consumePurchaseConfirmed(message2);

    //then: 서로 다른 이벤트이므로 각각 처리 (2번)
    verify(orderService, times(2)).createDropOrder(any());
    verify(processedEventRepository, times(2)).save(any());
  }

  private String buildMessage(String eventId) {
    return "{"
        + "\"eventId\":\"" + eventId + "\","
        + "\"orderId\":\"" + UUID.randomUUID() + "\","
        + "\"dropId\":\"" + UUID.randomUUID() + "\","
        + "\"userId\":\"" + UUID.randomUUID() + "\","
        + "\"productId\":\"" + UUID.randomUUID() + "\","
        + "\"holdExpiresAt\":\"2026-06-30T23:00:00\""
        + "}";
  }
}
