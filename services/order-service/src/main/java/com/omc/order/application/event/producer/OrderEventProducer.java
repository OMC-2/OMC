package com.omc.order.application.event.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.order.application.event.dto.OrderCancelledEvent;
import com.omc.order.application.event.dto.OrderConfirmedEvent;
import com.omc.order.application.event.dto.OrderCreatedEvent;
import com.omc.order.application.event.dto.OrderShippedEvent;
import com.omc.order.application.event.dto.RefundRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventProducer {

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;

  //1. 주문 생성 (결제 요청 트리거) -> payment
  public void sendOrderCreated(OrderCreatedEvent payload) {
    sendMessage("order.created", payload, payload.orderId(), "주문 생성 이벤트(결제 요청 트리거)");
  }

  //2. 주문 취소 (래플 재추첨 / 알림 트리거) -> notification, raffle 이벤트 발행
  public void sendOrderCancelled(OrderCancelledEvent payload) {
    sendMessage("order.cancelled", payload, payload.orderId(), "주문 취소 이벤트(래플 재추첨/알림 트리거)");
  }

  //3. 주문 확정 -> notification
  public void sendOrderConfirmed(OrderConfirmedEvent payload) {
    sendMessage("order.confirmed", payload, payload.orderId(), "주문 확정 이벤트(알림 트리거)");
  }

  //4. 배송 시작 -> notification
  public void sendOrderShipped(OrderShippedEvent payload) {
    sendMessage("order.shipped", payload, payload.orderId(), "배송 시작 이벤트(알림 트리거)");
  }

  //5. 환불 요청(사용자 직접) -> payment
  public void sendRefundRequested(RefundRequestedEvent payload) {
    sendMessage("refund.requested", payload, payload.orderId(), "환불 요청 이벤트(PG 취소 트리거)");
  }

  //[DLQ 재발행 전용] 이미 직렬화된 원본 payload(JSON 문자열)를 원본 토픽으로 그대로 다시 발행
  //DTO를 재구성하지 않고 보존된 바이트를 그대로 흘려보냄 -> 최초 수신과 동일한 형태로 consumer 재처리
  //@param key null 가능 (원본 메시지에 키가 없었던 경우)
  public void republishRaw(String topic, String key, String rawPayload) {
    log.info("[DLQ 재발행] topic={}, key={}", topic, key);
    if (key != null) {
      kafkaTemplate.send(topic, key, rawPayload);
    } else {
      kafkaTemplate.send(topic, rawPayload);
    }
  }

  private void sendMessage(String topic, Object payload, UUID orderId, String description) {
    try {
      String message = objectMapper.writeValueAsString(payload);
      log.info("{} 발행 : topic={}, orderId={}", description, topic, orderId);
      kafkaTemplate.send(topic, message);
    } catch (JsonProcessingException e) {
      log.error("{} 직렬화 실패: topic={}, orderId={}", payload.getClass().getSimpleName(), topic, orderId, e);
      throw new org.springframework.kafka.KafkaException("이벤트 직렬화 실패", e);
    }
  }
}
