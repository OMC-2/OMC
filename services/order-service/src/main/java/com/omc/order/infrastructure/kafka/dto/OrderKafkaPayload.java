package com.omc.order.infrastructure.kafka.dto;

import java.util.UUID;

public class OrderKafkaPayload {

  //[드롭] 선착순 재고 선점 완료 이벤트 페이로드
  //drop-service (purchase.confirmed) -> order-service 수신
  public record PurchaseConfirmed(
      String eventId,
      UUID orderId,
      UUID dropId,
      UUID userId,
      UUID productId
  ){}

  //[래플] 추첨 당첨자 선정 완료 이벤트 페이로드 (Order-first)
  //raffle-service (raffle.winner.selected) -> order-service 수신
  public record RaffleWinnerSelected(
      String eventId,
      UUID entryId,
      UUID raffleId,
      UUID userId,
      UUID productId,
      UUID billingKeyId,
      UUID couponId,
      Long originalAmount,
      Long discountAmount,
      Long finalAmount
  ) {}

  //[공통] 결제 완료 이벤트 페이로드
  //payment-service (payment.completed) -> order-service 수신
  public record PaymentCompleted(
      String eventId,
      String salesType,
      UUID userId,
      UUID couponId,
      Long originalAmount,
      Long discountAmount,
      Long finalAmount,
      UUID orderId,
      UUID entryId
  ) {}
}
