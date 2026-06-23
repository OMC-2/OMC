package com.omc.notification.presentation.dto.response;

import com.omc.notification.domain.entity.Notification;
import com.omc.notification.domain.enums.NotificationStatus;
import com.omc.notification.domain.enums.NotificationType;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID notificationId,
        NotificationType notificationType,
        String title,
        String content,
        UUID referenceId,
        String referenceType,
        NotificationStatus status,
        boolean isRead,
        LocalDateTime sentAt,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getNotificationId(),
                notification.getNotificationType(),
                notification.getTitle(),
                notification.getContent(),
                notification.getReferenceId(),
                notification.getReferenceType(),
                notification.getStatus(),
                notification.isRead(),
                notification.getSentAt(),
                notification.getCreatedAt()
        );
    }
}
