package com.omc.order.application.event.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.order.application.event.dto.OrderCancelledEvent;
import com.omc.order.application.event.dto.OrderCreatedEvent;
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

  //1. 주문 생성 이벤트 발행
  public void sendOrderCreated(OrderCreatedEvent payload) {
    sendMessage("order.created", payload, payload.orderId(), "주문 생성 이벤트(결제 요청 트리거)");
  }

  //2. 주문 취소 이벤트 발행
  public void sendOrderCancelled(OrderCancelledEvent payload) {
    sendMessage("order.cancelled", payload, payload.orderId(), "주문 취소 이벤트(래플 재추첨/알림 트리거)");
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
