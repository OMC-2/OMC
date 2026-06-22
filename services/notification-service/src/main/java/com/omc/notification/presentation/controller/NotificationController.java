package com.omc.notification.presentation.controller;

import com.omc.common.exception.BusinessException;
import com.omc.common.exception.CommonErrorCode;
import com.omc.common.response.ApiResponse;
import com.omc.common.response.PageResponse;
import com.omc.common.security.SecurityUtil;
import com.omc.notification.application.service.NotificationService;
import com.omc.notification.presentation.dto.response.NotificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<PageResponse<NotificationResponse>> getMyNotifications(Pageable pageable) {
        UUID userId = SecurityUtil.getCurrentUserId()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        return ApiResponse.success(new PageResponse<>(notificationService.getMyNotifications(userId, pageable)));
    }

    @PatchMapping("/{notificationId}/read")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Void> markAsRead(@PathVariable UUID notificationId) {
        UUID userId = SecurityUtil.getCurrentUserId()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        notificationService.markAsRead(userId, notificationId);
        return ApiResponse.success(null);
    }
}
