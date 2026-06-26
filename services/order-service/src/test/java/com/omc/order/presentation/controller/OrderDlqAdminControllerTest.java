package com.omc.order.presentation.controller;

import com.omc.common.response.ApiResponse;
import com.omc.common.response.PageResponse;
import com.omc.order.application.service.OrderDlqService;
import com.omc.order.domain.enums.DlqStatus;
import com.omc.order.presentation.dto.response.DlqMessageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrderDlqAdminController 단위 테스트.
 * presentation.controller 커버리지(jacoco).
 */
@ExtendWith(MockitoExtension.class)
class OrderDlqAdminControllerTest {

  @Mock OrderDlqService dlqService;

  @InjectMocks OrderDlqAdminController controller;

  @Test
  @DisplayName("DLQ 목록 조회: 서비스 결과를 PageResponse로 감싸 200 OK")
  void getMessages() {
    Pageable pageable = PageRequest.of(0, 20);
    Page<DlqMessageResponse> page = new PageImpl<>(List.of());
    when(dlqService.getMessages(eq(DlqStatus.FAILED), any(Pageable.class))).thenReturn(page);

    ResponseEntity<ApiResponse<PageResponse<DlqMessageResponse>>> response =
        controller.getMessages(DlqStatus.FAILED, pageable);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getData().getContent()).isEmpty();
    verify(dlqService).getMessages(eq(DlqStatus.FAILED), any(Pageable.class));
  }

  @Test
  @DisplayName("DLQ 재발행: 서비스에 위임하고 200 OK")
  void republish() {
    UUID dlqId = UUID.randomUUID();

    ResponseEntity<ApiResponse<Void>> response = controller.republish(dlqId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().isSuccess()).isTrue();
    verify(dlqService).republish(dlqId);
  }
}