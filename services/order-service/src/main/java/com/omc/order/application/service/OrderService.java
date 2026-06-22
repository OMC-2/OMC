package com.omc.order.application.service;

import com.omc.common.response.ApiResponse;
import com.omc.order.application.event.dto.OrderCancelledEvent;
import com.omc.order.application.event.dto.OrderConfirmedEvent;
import com.omc.order.application.event.dto.OrderCreatedEvent;
import com.omc.order.application.event.dto.OrderShippedEvent;
import com.omc.order.application.event.dto.PurchaseConfirmedEvent;
import com.omc.order.application.event.dto.RaffleWinnerSelectedEvent;
import com.omc.order.application.event.dto.RefundRequestedEvent;
import com.omc.order.application.event.producer.OrderEventProducer;
import com.omc.order.domain.entity.Order;
import com.omc.order.domain.enums.CancelReason;
import com.omc.order.domain.exception.OrderNotFoundException;
import com.omc.order.domain.repository.OrderRepository;
import com.omc.order.infrastructure.client.ProductFeignClient;
import com.omc.order.infrastructure.client.dto.ProductResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

  private final OrderEventProducer orderEventProducer;
  private final ProductFeignClient productFeignClient;
  private final OrderRepository orderRepository;


  //1. [DROP] 선착순 재고 선점 완료 후 임시 주문 생성
  @Transactional
  public void createDropOrder(PurchaseConfirmedEvent payload) {
    log.info("[OrderService] 드롭 주문 생성 시작: orderId={}, productId={}", payload.orderId(), payload.productId());

      //1. 가격(원가)은 purchase.confirmed 에 없으므로, Internal API 동기 조회
      //Feign 실패(상품 서비스 일시 장애 등)는 그대로 전파 -> DefaultErrorHandler가 재시도/DLQ 처리
      ApiResponse<ProductResponse> productResponse = productFeignClient.getProductDetail(payload.productId());
      if (productResponse == null || productResponse.getData() == null) {
        throw new IllegalStateException("상품 정보를 조회할 수 없습니다. productId=" + payload.productId());
      }
      Long originalAmount = productResponse.getData().price();

      //2. drop이 생성한 orderId를 그대로 PK로 사용 (ID 일관성: purchase.confirmed = order.created)
      Order order = Order.createDropOrder(
          payload.orderId(),
          payload.userId(),
          payload.productId(),
          payload.dropId(),
          originalAmount,
          payload.eventId());
      orderRepository.save(order); //DB에 1차 저장

      //3. 결제 요청을 위한 order.created 이벤트 발행
      OrderCreatedEvent createdEvent = OrderCreatedEvent.builder()
          .eventId(UUID.randomUUID().toString())
          .orderId(order.getOrderId())
          .userId(payload.userId())
          .orderType("DROP")
          .dropId(payload.dropId())
          .raffleId(null)
          .entryId(null)
          .originalAmount(originalAmount)
          .discountAmount(0L)
          .finalAmount(originalAmount)
          .couponId(null) // 결제 전이므로 null
          .billingKeyId(null) //드롭은 수동 결제이므로 null
          .build();
      orderEventProducer.sendOrderCreated(createdEvent);
  }

  //2. [RAFFLE] 추첨 당첨자 선정 완료 후 임시 주문 생성 (order-first, PENDING_PAYMENT)
  @Transactional
  public void createRaffleOrder(RaffleWinnerSelectedEvent payload) {
    log.info("[OrderService] 래플 주문 생성 시작: entryId={}", payload.entryId());

    //래플은 상류에서 orderId를 주지 않으므로 order가 생성
    UUID orderId = UUID.randomUUID();

    Order order = Order.createRaffleOrder(
        orderId,
        payload.userId(),
        payload.productId(),
        payload.raffleId(),
        payload.entryId(),
        payload.couponId(),
        payload.originalAmount(),
        payload.discountAmount(),
        payload.finalAmount(),
        payload.eventId());
    orderRepository.save(order);

    //1. 결제 요청을 위한 order.created 이벤트 발행 (billingKeyId 포함 -> payment 가 capture)
    OrderCreatedEvent createdEvent = OrderCreatedEvent.builder()
        .eventId(UUID.randomUUID().toString())
        .orderId(order.getOrderId())
        .userId(payload.userId())
        .orderType("RAFFLE")
        .dropId(null)
        .raffleId(payload.raffleId())
        .entryId(payload.entryId())
        .originalAmount(payload.originalAmount())
        .discountAmount(payload.discountAmount())
        .finalAmount(payload.finalAmount())
        .couponId(payload.couponId())
        .billingKeyId(payload.billingKeyId())
        .build();
    orderEventProducer.sendOrderCreated(createdEvent);
  }

  //[결제 완료] payment.completed -> PENDING_PAYMENT -> PAID
  @Transactional
  public void markOrderPaid(UUID orderId, UUID paymentId) {
    log.info("[OrderService] 결제 완료 처리: orderId={}, paymentId={}", orderId, paymentId);
    Order order = findOrder(orderId);
    order.markPaid(paymentId);
    //재고 확정 차감(stock.deducted)을 기다린다. order.created 후 product가 차감
  }

  //[재고 확정 차감 완료] stock.deducted -> PAID -> CONFIRMED + order.confirmed 발행
  @Transactional
  public void confirmOrder(UUID orderId) {
    log.info("[OrderService] 주문 확정 처리: orderId={}", orderId);
    Order order = findOrder(orderId);
    order.confirm();

    OrderConfirmedEvent confirmedEvent = OrderConfirmedEvent.builder()
        .eventId(UUID.randomUUID().toString())
        .orderId(order.getOrderId())
        .userId(order.getUserId())
        .build();
    orderEventProducer.sendOrderConfirmed(confirmedEvent);
  }

  //[공통] 재고 차감 실패/ 홀드 만료/ 결제 실패로 인한 주문 취소 처리 -> order.cancelled 발행
  @Transactional
  public void cancelOrder(UUID orderId, CancelReason reason) {
    log.info("[OrderService] 주문 취소 처리 시작: orderId={}, 사유={}", orderId, reason);

    //1. 주문 상태를 CANCELLED로 전이
    Order order = findOrder(orderId);
    order.cancel(reason);

    //2.래플 재추첨 및 유저 알림을 위한 order.cancelled 이벤트 발행
    OrderCancelledEvent cancelledEvent = OrderCancelledEvent.builder()
        .eventId(UUID.randomUUID().toString())
        .orderId(orderId)
        .userId(order.getUserId())
        .raffleId(order.getRaffleId())
        .entryId(order.getEntryId())
        .reason(reason.name())
        .build();
    orderEventProducer.sendOrderCancelled(cancelledEvent);
  }

  //[배송 시작] 배송 스케줄러에서 호출: CONFIRMED -> SHIPPING + order.shipped 발행
  @Transactional
  public void startShipping(UUID orderId) {
    log.info("[OrderService] 배송 시작 처리: orderId={}", orderId);
    Order order = findOrder(orderId);
    order.startShipping();

    OrderShippedEvent shippedEvent = OrderShippedEvent.builder()
        .eventId(UUID.randomUUID().toString())
        .orderId(order.getOrderId())
        .userId(order.getUserId())
        .build();
    orderEventProducer.sendOrderShipped(shippedEvent);
  }

  //[사용자 환불 요청] CONFIRMED -> REFUND_REQUESTED + refund.requested 발행 (payment 가 PG 취소)
  @Transactional
  public void requestRefund(UUID orderId, CancelReason reason) {
    log.info("[OrderService] 환불 요청 처리: orderId={}, reason={}", orderId, reason);
    Order order = findOrder(orderId);
    order.requestRefund(reason);

    RefundRequestedEvent refundEvent = RefundRequestedEvent.builder()
        .eventId(UUID.randomUUID().toString())
        .orderId(order.getOrderId())
        .userId(order.getUserId())
        .reason(reason.name())
        .build();
    orderEventProducer.sendRefundRequested(refundEvent);
  }

  private Order findOrder(UUID orderId) {
    return orderRepository.findById(orderId)
        .orElseThrow(OrderNotFoundException::new);
  }
}


