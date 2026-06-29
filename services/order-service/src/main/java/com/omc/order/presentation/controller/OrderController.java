package com.omc.order.presentation.controller;

import com.omc.common.response.ApiResponse;
import com.omc.order.application.service.OrderService;
import com.omc.order.presentation.dto.response.OrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Order API", description = "주문 생성 및 조회 관련 API")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

  private final OrderService orderService;

  //주문 단건 조회: E2E 폴링/상태 확인 및 사용자 주문 상세 조회
  @Operation(summary = "주문 단건 조회", description = "주문 ID로 주문 상태와 상세 정보를 조회합니다.")
  @GetMapping("/{orderId}")
  public ResponseEntity<ApiResponse<OrderResponse>> getOrder(@PathVariable UUID orderId){
    return ResponseEntity.ok(ApiResponse.success(orderService.getOrder(orderId)));
  }
}
