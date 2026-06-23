package com.omc.order.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CancelReason {
  PAYMENT_FAILED("결제 실패"), //payment.failed 수신 (PG 원문은 payment 도메인이 보관)
  HOLD_EXPIRED("홀드 만료"), //hold.expired 수신 (드롭 TIL 미결제)
  STOCK_DEDUCT_FAILED("재고 차감 실패"), //stock.failed 수신 (결제 후 차감 실패)
  USER_REQUESTED("사용자 환불 요청"); //사용자 직접 환불 (CONFIRMED -> REFUND_REQUESTED)

  private final String description;
}
