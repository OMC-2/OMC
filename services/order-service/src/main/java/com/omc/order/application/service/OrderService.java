package com.omc.order.application.service;

import com.omc.common.response.ApiResponse;
import com.omc.order.application.event.dto.OrderCancelledEvent;
import com.omc.order.application.event.dto.OrderCreatedEvent;
import com.omc.order.application.event.dto.PurchaseConfirmedEvent;
import com.omc.order.application.event.dto.RaffleWinnerSelectedEvent;
import com.omc.order.application.event.producer.OrderEventProducer;
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
  //private final OrderRepository orderRepository;

  //1. [DROP] 선착순 재고 선점 완료 후 임시 주문 생성
  @Transactional
  public void createDropOrder(PurchaseConfirmedEvent payload) {
    log.info("[OrderService] 드롭 주문 생성 시작: orderId={}, productId={}", payload.orderId(), payload.productId());

    try {
      //1. 실시간 상품 가격(원가) 동기 조회(캐시 백업)
      ApiResponse<ProductResponse> productResponse = productFeignClient.getProductDetail(payload.productId());
      if (productResponse == null || productResponse.getData() == null) {
        throw new IllegalStateException("상품 정보를 조회할 수 없습니다. productId=" + payload.productId());
      }

      Long finalAmount = productResponse.getData().price();

      //2. DB에 PENDING_PAYMENT 상태로 주문 엔티티 저장
      //Order order = Order.createDropOrder(payload.orderId(), payload.userId(), payload.productId(), originalAmount, finalAmount, payload.dropId());
      //orderRepository.save(order);

      //3. 결제 요청을 위한 order.created 이벤트 발행
      OrderCreatedEvent createdEvent = OrderCreatedEvent.builder()
          .eventId(UUID.randomUUID().toString())
          .orderId(payload.orderId())
          .userId(payload.userId())
          .orderType("DROP")
          .dropId(payload.dropId())
          .raffleId(null)
          .entryId(null)
          .finalAmount(finalAmount)
          .couponId(null) // 결제 전이므로 null
          .billingKeyId(null) //드롭은 수동 결제이므로 null
          .build();

      orderEventProducer.sendOrderCreated(createdEvent);

    } catch (Exception e) {
      // 404 예외 발생 시 에러 처리 분기
      log.error("[OrderService] 드롭 주문 생성 실패 - failed-log 격리 대상: orderId={}", payload.orderId(), e);
      throw new org.springframework.kafka.KafkaException("주문 생성 중 예외 발생으로 인한 롤백", e);
    }
  }

  //2. [RAFFLE] 추첨 당첨자 선정 완료 후 임시 주문 생성
  @Transactional
  public void createRaffleOrder(RaffleWinnerSelectedEvent payload) {
    UUID newOrderId = UUID.randomUUID();

    log.info("[OrderService] 래플 주문 생성 시작: entryId={}, orderId={}", payload.entryId(), newOrderId);

    //Order order = Order.createRaffleOrder(newOrderId, payload.userId(), payload.productId(), payload.finalAmount(), payload.raffleId(), payload.entryId());
    //orderRepository.save(order);

    //1. 결제 요청을 위한 order.created 이벤트 발행
    OrderCreatedEvent createdEvent = OrderCreatedEvent.builder()
        .eventId(UUID.randomUUID().toString())
        .orderId(newOrderId)
        .userId(payload.userId())
        .orderType("RAFFLE")
        .dropId(null)
        .raffleId(payload.raffleId())
        .entryId(payload.entryId())
        .finalAmount(payload.finalAmount())
        .couponId(payload.couponId())
        .billingKeyId(payload.billingKeyId())
        .build();

    orderEventProducer.sendOrderCreated(createdEvent);
  }

  // [공통] 결제 실패 또는 타임아웃으로 인한 주문 취소 처리
  @Transactional
  public void cancelOrder(UUID orderId, String reason) {
    log.info("[OrderService] 주문 취소 처리 시작: orderId={}, 사유={}", orderId, reason);

    //1. 주문 상태를 CANCELLED로 전이
    //Order order = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    //order.cancel(reason);

    //2.래플 재추첨 및 유저 알림을 위한 order.cancelled 이벤트 발행
    OrderCancelledEvent cancelledEvent = OrderCancelledEvent.builder()
        .eventId(UUID.randomUUID().toString())
        .orderId(orderId)
        //.userId(order.getUserId())
        //.raffleId(order.getRaffleId())
        //.entryId(order.getEntryId())
        .reason(reason)
        .build();

    orderEventProducer.sendOrderCancelled(cancelledEvent);
  }
}
