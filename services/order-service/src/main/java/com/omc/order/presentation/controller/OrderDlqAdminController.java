package com.omc.order.presentation.controller;

import com.omc.common.response.ApiResponse;
import com.omc.common.response.PageResponse;
import com.omc.order.application.service.OrderDlqService;
import com.omc.order.domain.enums.DlqStatus;
import com.omc.order.presentation.dto.response.DlqMessageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

//DLQ 운영용 관리자 API

@Tag(name = "Order DLQ Admin", description = "주문 서비스 DLQ 운영 API(관리자 전용)")
@RestController
@RequestMapping("/api/v1/admin/dlq")
@RequiredArgsConstructor
public class OrderDlqAdminController {

  private final OrderDlqService dlqService;

  @Operation(summary = "DLQ 메시지 목록 조회", description = "상태별(FALED/RESOLVED) DLQ 메시지를 페이징 조회합니다.")
  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<DlqMessageResponse>>> getMessages(
      @RequestParam(defaultValue = "FAILED") DlqStatus status,
      @PageableDefault(size = 20) Pageable pageable) {
    Page<DlqMessageResponse> result = dlqService.getMessages(status, pageable);
    return ResponseEntity.ok(ApiResponse.success(new PageResponse<>(result)));
  }

  @Operation(summary = "DLQ 메시지 재발행", description = "보존된 원본 payload를 원본 토픽으로 재발행하고, RESOLVED로 전이합니다.")
  @PostMapping("/{dlqId}/republish")
  public ResponseEntity<ApiResponse<Void>> republish(@PathVariable UUID dlqId) {
    dlqService.republish(dlqId);
    return ResponseEntity.ok(ApiResponse.ok());
  }
}
