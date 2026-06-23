package com.omc.order.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderStatus {

  //진행 상태
  PENDING_PAYMENT("결제 대기"), //가생성 상태(드롭 전용)
  PAID("결제 완료"), //결제 완료 (재고 확정 차감 대기)
  CONFIRMED("주문 확정"), //재고 차감 완료 및 래플 당첨 확정
  SHIPPING("배송중"), //배송 스케줄러에 의해 전이
  REFUND_REQUESTED("환불 요청 접수"), //유저 환불 요청 (PG 취소 진행 중)

  //종단 상태 (더 이상 다른 상태로 변하지 않음)
  DELIVERED("배송 완료"),
  CANCELLED("주문 취소"), //타임아웃, 결제 실패, 재고 차감 실패
  REFUNDED("환불 완료"); //PG 환불 완료

  private final String description;
}
