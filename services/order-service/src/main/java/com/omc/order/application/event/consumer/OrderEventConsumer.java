package com.omc.order.application.event.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.order.application.event.dto.HoldExpiredEvent;
import com.omc.order.application.event.dto.PaymentCompletedEvent;
import com.omc.order.application.event.dto.PaymentFailedEvent;
import com.omc.order.application.event.dto.PurchaseConfirmedEvent;
import com.omc.order.application.event.dto.RaffleWinnerSelectedEvent;
import com.omc.order.application.event.dto.StockDeductedEvent;
import com.omc.order.application.event.dto.StockFailedEvent;
import com.omc.order.application.service.OrderService;
import com.omc.order.domain.entity.ProcessedEvent;
import com.omc.order.domain.enums.CancelReason;
import com.omc.order.domain.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

  private final ObjectMapper objectMapper;
  private final ProcessedEventRepository processedEventRepository;
  private final OrderService orderService;

  //공통 유틸
  //역직렬화 실패는 재시도해도 동일, 재시도 불가 예외(MessageConversionException)로 변환해 즉시 DLQ 유도
  private <T> T parse(String message, Class<T> type, String topic) {
    try {
      return objectMapper.readValue(message, type);
    } catch (JsonProcessingException e) {
      throw new MessageConversionException("이벤트 역직렬화 실패: topic=" + topic, e);
    }
  }

  //[멱등성 검증] 이미 처리된 event_id이면 true. 리스너 트랜잭션 안에서 호출되어
  // 비즈니스 로직이 롤백되면 ProcessedEvent 저장도 함께 롤백 (재시도 시 재처리 보장)
  private boolean isAlreadyProcessed(String eventId, String topic){
    if(processedEventRepository.existsById(eventId)){
      log.warn("[Idempotency] 이미 처리된 이벤트입니다. 무시합니다. eventId={}, topic={}", eventId, topic);
      return true;
    }
    //처리 내역 저장
    processedEventRepository.save(ProcessedEvent.create(eventId, topic));
    return false;
  }

  //리스너
  //비즈니스 예외(OrderNotFoundException, OrderStateException 등)는 잡지 않고 전파
  //분류/재시도/DLQ는 DefaultErrorHandler(KafkaConsumerConfig)가 담당

  //1.래플 당첨 수신 -> PENDING_PAYMENT 주문 생성
  @KafkaListener(topics = "raffle.winner.selected", groupId = "order-service-group")
  @Transactional
  public void consumeRaffleWinnerSelected(String message) {
      RaffleWinnerSelectedEvent payload = parse(message, RaffleWinnerSelectedEvent.class, "raffle.winner.selected");

      //멱등성 방어 차단 로직
      if (isAlreadyProcessed(payload.eventId(), "raffle.winner.selected")) return;
      log.info("[OrderConsumer] 래플 당첨 수신 -> PENDING 주문 생성 대기: Entry ID = {}", payload.entryId());
      orderService.createRaffleOrder(payload);
  }

  //2. 드롭 선점 수신 -> PENDING_PAYMENT 주문 생성
  @KafkaListener(topics = "purchase.confirmed", groupId = "order-service-group")
  @Transactional
  public void consumePurchaseConfirmed(String message) {
      PurchaseConfirmedEvent payload = parse(message, PurchaseConfirmedEvent.class, "purchase.confirmed");
      if (isAlreadyProcessed(payload.eventId(), "purchase.confirmed")) return;
      log.info("[OrderConsumer] 드롭 선점 수신 -> PENDING 주문 생성 대기: Order Id = {}", payload.orderId());
      orderService.createDropOrder(payload);
    }


  //3. 결제 완료 수신 -> PENDING_PAYMENT -> PAID
  @KafkaListener(topics = "payment.completed", groupId = "order-service-group")
  @Transactional
  public void consumePaymentCompleted(String message) {
      PaymentCompletedEvent payload = parse(message,PaymentCompletedEvent.class, "payment.completed");
      if (isAlreadyProcessed(payload.eventId(), "payment.completed")) return;

      log.info("[OrderConsumer] 결제 완료 수신 -> PAID 전이: Order Id = {}", payload.orderId());
      orderService.markOrderPaid(payload.orderId(), payload.paymentId());
  }

  //4. 결제 실패 수신 (SAGA 롤백용) -> 주문 취소 + order.cancelled
  @KafkaListener(topics = "payment.failed", groupId = "order-service-group")
  @Transactional
  public void consumePaymentFailed(String message) {
      PaymentFailedEvent payload = parse(message, PaymentFailedEvent.class, "payment.failed");
      if (isAlreadyProcessed(payload.eventId(), "payment.failed")) return;

      log.info("[OrderConsumer] 결제 실패 수신 -> 주문 취소 처리: orderId = {}, PG사유 = {}", payload.orderId(), payload.failureReason());
      orderService.cancelOrder(payload.orderId(), CancelReason.PAYMENT_FAILED);
  }

  //5. 재고 확정 차감 완료 수신 -> PAID -> CONFIRMED + order.confirmed
  @KafkaListener(topics = "stock.deducted", groupId = "order-service-group")
  @Transactional
  public void consumeStockDeducted(String message) {
      StockDeductedEvent payload = parse(message, StockDeductedEvent.class, "stock.deducted");
      if(isAlreadyProcessed(payload.eventId(), "stock.deducted")) return;

      log.info("[OrderConsumer] 재고 차막 완료 수신 -> CONFIRMED 전이: orderId={}", payload.orderId());
      orderService.confirmOrder(payload.orderId());
  }

  //6.재고 차감 실패 수신 (결제 후 차감 실패) -> 주문 취소
  @KafkaListener(topics = "stock.failed", groupId = "order-service-group")
  @Transactional
  public void consumeStockFailed(String message) {
      StockFailedEvent payload = parse(message, StockFailedEvent.class, "stock.failed");
      if (isAlreadyProcessed(payload.eventId(), "stock.failed")) return;

      log.info("[OrderConsumer] 재고 차감 실패 수신 -> 주문 취소: orderId={}", payload.orderId());
      orderService.cancelOrder(payload.orderId(), CancelReason.STOCK_DEDUCT_FAILED);
  }

  //7.홀드 만료 수신 (드롭 TTL 만료, 미결제) PENDING 주문 취소
  @KafkaListener(topics = "hold.expired", groupId = "order-service-group")
  @Transactional
  public void consumeHoldExpired(String message) {
      HoldExpiredEvent payload = parse(message, HoldExpiredEvent.class, "hold.expired");
      if (isAlreadyProcessed(payload.eventId(), "hold.expired")) return;

      log.info("[OrderConsumer] 홀드 만료 수신 -> 주문 취소: orderId={}", payload.orderId());
      orderService.cancelOrder(payload.orderId(), CancelReason.HOLD_EXPIRED);
  }
}
