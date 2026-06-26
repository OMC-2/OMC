package com.omc.order.presentation.controller;

import com.omc.common.response.ApiResponse;
import com.omc.order.application.service.OrderService;
import com.omc.order.domain.enums.CancelReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * OrderRefundController 단위 테스트.
 * presentation.controller 커버리지(jacoco) 충족용. 서비스 위임 + 응답 검증.
 */
@ExtendWith(MockitoExtension.class)
class OrderRefundControllerTest {

  @Mock OrderService orderService;

  @InjectMocks OrderRefundController controller;

  @Test
  @DisplayName("환불 요청: 서비스에 USER_REQUESTED로 위임하고 200 OK")
  void requestRefund() {
    UUID orderId = UUID.randomUUID();

    ResponseEntity<ApiResponse<Void>> response = controller.requestRefund(orderId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().isSuccess()).isTrue();
    verify(orderService).requestRefund(orderId, CancelReason.USER_REQUESTED);
  }
}