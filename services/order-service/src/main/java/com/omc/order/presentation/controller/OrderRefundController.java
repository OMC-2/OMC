package com.omc.order.presentation.controller;

import com.omc.common.response.ApiResponse;
import com.omc.order.application.service.OrderService;
import com.omc.order.domain.enums.CancelReason;
import com.omc.order.presentation.dto.response.OrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Order", description = "주문 API")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderRefundController {

  private final OrderService orderService;

  //사용자 직접 환불 요청: CONFIRMED -> REFUND_REQUESTED -> refund.requested 발행
  //상태가 CONFIRMED 가 아니면 OrderStateException(REFUND_NOT_ALLOWED) -> GlobalExceptionHandeler 가 403 응답
  @Operation(summary = "주문 환불 요청", description = "확정된 주문에 대해 사용자가 환불을 요청합니다.")
  @PostMapping("/{orderId}/refund")
  public ResponseEntity<ApiResponse<Void>> requestRefund(@PathVariable UUID orderId) {
    orderService.requestRefund(orderId, CancelReason.USER_REQUESTED);
    return ResponseEntity.ok(ApiResponse.ok());
  }
}
