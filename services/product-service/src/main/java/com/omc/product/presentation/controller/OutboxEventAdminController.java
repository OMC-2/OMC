package com.omc.product.presentation.controller;

import com.omc.common.response.ApiResponse;
import com.omc.product.application.service.OutboxEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Outbox Admin", description = "Outbox 이벤트 관리 (어드민 전용)")
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/v1/admin/outbox-events")
@RequiredArgsConstructor
public class OutboxEventAdminController {

    private final OutboxEventService outboxEventService;

    @Operation(summary = "Outbox 이벤트 단건 재처리",
        description = "FAILED 상태의 이벤트를 INIT으로 초기화합니다. Poller가 다음 주기에 자동 재발행합니다.")
    @PostMapping("/{eventId}/retry")
    public ResponseEntity<ApiResponse<Void>> retryFailedEvent(@PathVariable UUID eventId) {
        outboxEventService.retryFailedEvent(eventId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(summary = "Outbox 이벤트 전체 재처리",
        description = "FAILED 상태 전체 이벤트를 INIT으로 초기화합니다. 처리된 건수를 반환합니다.")
    @PostMapping("/retry-all")
    public ResponseEntity<ApiResponse<Integer>> retryAllFailedEvents() {
        int count = outboxEventService.retryAllFailedEvents();
        return ResponseEntity.ok(ApiResponse.success(count));
    }
}
