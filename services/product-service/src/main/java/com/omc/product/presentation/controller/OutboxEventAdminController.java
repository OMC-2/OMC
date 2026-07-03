package com.omc.product.presentation.controller;

import com.omc.common.response.ApiResponse;
import com.omc.product.application.service.OutboxEventService;
import com.omc.product.presentation.dto.response.OutboxRetryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/outbox-events")
@RequiredArgsConstructor
public class OutboxEventAdminController {

    private final OutboxEventService outboxEventService;

    /**
     * FAILED 단건 수동 재처리
     * INIT으로 초기화 후 Poller가 다음 주기에 자동 재발행
     */
    @PostMapping("/{eventId}/retry")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> retryFailedEvent(@PathVariable UUID eventId) {
        outboxEventService.retryFailedEvent(eventId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /**
     * FAILED 전체 수동 재처리
     */
    @PostMapping("/retry-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OutboxRetryResponse>> retryAllFailedEvents() {
        int count = outboxEventService.retryAllFailedEvents();
        return ResponseEntity.ok(ApiResponse.success(new OutboxRetryResponse(count)));
    }
}

