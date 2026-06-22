package com.omc.notification.domain.entity;

import com.omc.common.entity.BaseEntity;
import com.omc.notification.domain.enums.NotificationStatus;
import com.omc.notification.domain.enums.NotificationType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "p_notifications", schema = "notification_db")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "notification_id")
    private UUID notificationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "slack_id", nullable = false, length = 100)
    private String slackId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 50)
    private NotificationType notificationType;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Builder
    private Notification(UUID userId, String slackId, NotificationType notificationType,
                         String title, String content, UUID referenceId, String referenceType) {
        this.userId = userId;
        this.slackId = slackId;
        this.notificationType = notificationType;
        this.title = title;
        this.content = content;
        this.referenceId = referenceId;
        this.referenceType = referenceType;
        this.status = NotificationStatus.PENDING;
        this.retryCount = 0;
        this.isRead = false;
    }

    public static Notification create(UUID userId, String slackId, NotificationType type,
                                      String title, String content, UUID referenceId, String referenceType) {
        return Notification.builder()
                .userId(userId)
                .slackId(slackId)
                .notificationType(type)
                .title(title)
                .content(content)
                .referenceId(referenceId)
                .referenceType(referenceType)
                .build();
    }

    public void markSuccess() {
        this.status = NotificationStatus.SUCCESS;
        this.sentAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.status = NotificationStatus.FAILED;
        this.retryCount++;
    }

    public void markRead() {
        this.isRead = true;
    }

    public boolean isRetryable() {
        return this.retryCount < 3;
    }

    public void resetToPending() {
        this.status = NotificationStatus.PENDING;
    }
}
