package com.omc.order.infrastructure.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.order.domain.entity.ProcessedEvent;
import com.omc.order.domain.repository.ProcessedEventRepository;
import com.omc.order.infrastructure.kafka.dto.OrderKafkaPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

  private final ObjectMapper objectMapper;
  private final ProcessedEventRepository processedEventRepository;

  //private final OrderService orderService;

  //[멱등성 검증] 이미 처리된 event_id인지 확인 후, 없다면 저장
  private boolean isAlreadyProcessed(String eventId, String topic){
    if(processedEventRepository.existsById(eventId)){
      log.warn("[Idempotency] 이미 처리된 이벤트입니다. 무시합니다. eventId: {}, topic: {}", eventId, topic);
      return true;
    }
    //처리 내역 저장
    processedEventRepository.save(ProcessedEvent.create(eventId, topic));
    return false;
  }

  @KafkaListener(topics = "raffle.winner.selected", groupId = "order-service-group")
  @Transactional
  public void consumeRaffleWinnerSelected(String message) {
    try {
      OrderKafkaPayload.RaffleWinnerSelected payload = objectMapper.readValue(message, OrderKafkaPayload.RaffleWinnerSelected.class);

      //멱등성 방어 차단 로직
      if (isAlreadyProcessed(payload.eventId(), "raffle.winner.selected")) return;

      log.info("[OrderConsumer] 래플 당첨 수신 -> PENDING 주문 생성 대기: Entry ID = {}", payload.entryId());
      //TODO: orderService.createRaffleOrder(payload) 호출
    } catch (Exception e){
      log.error("[OrderConsumer] raffle.winner.selected 파싱/처리 실패", e);
      throw new org.springframework.kafka.KafkaException("이벤트 처리 실패", e);
    }
  }

  @KafkaListener(topics = "purchase.confirmed", groupId = "order-service-group")
  @Transactional
  public void consumePurchaseConfirmed(String message) {
    try {
      OrderKafkaPayload.PurchaseConfirmed payload = objectMapper.readValue(message, OrderKafkaPayload.PurchaseConfirmed.class);

      if (isAlreadyProcessed(payload.eventId(), "purchase.confirmed")) return;

      log.info("[OrderConsumer] 드롭 선점 수신 -> PENDING 주문 생성 대기: Order Id = {}", payload.orderId());
      //TODO: orderService.createdDropOrder(payload) 호출
    } catch (Exception e){
      log.error("[OrderConsumer] purchase.confirmed 파싱/처리 실패", e);
    }
  }

  @KafkaListener(topics = "payment.completed", groupId = "order-service-group")
  @Transactional
  public void consumePaymentCompleted(String message) {
    try{
      OrderKafkaPayload.PaymentCompleted payload = objectMapper.readValue(message,OrderKafkaPayload.PaymentCompleted.class);
      if (isAlreadyProcessed(payload.eventId(), "payment.completed")) return;
      log.info("[OrderConsumer] 결제 완료 수신 -> 주문 확정 로직 진입: Order Id = {}", payload.orderId());
      //TODO: orderService.confirmOrder(payload) 호출
    } catch (Exception e) {
        log.error("[OrderConsumer] payment.completed 파싱/처리 실패", e);
        throw new org.springframework.kafka.KafkaException("이벤트 처리 실패", e);
    }
  }
}
