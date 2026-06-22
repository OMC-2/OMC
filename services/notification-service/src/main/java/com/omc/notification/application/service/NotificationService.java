package com.omc.notification.application.service;

import com.omc.notification.domain.entity.Notification;
import com.omc.notification.domain.entity.ProcessedEvent;
import com.omc.notification.domain.enums.NotificationType;
import com.omc.notification.domain.exception.NotificationErrorCode;
import com.omc.notification.domain.exception.NotificationNotFoundException;
import com.omc.notification.domain.repository.NotificationRepository;
import com.omc.notification.domain.repository.ProcessedEventRepository;
import com.omc.notification.infrastructure.client.SlackClient;
import com.omc.notification.infrastructure.client.UserServiceClient;
import com.omc.notification.presentation.dto.response.NotificationResponse;
import com.omc.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final UserServiceClient userServiceClient;
    private final SlackClient slackClient;

    @Transactional
    public void send(String eventId, String topic, UUID userId,
                     NotificationType type, String title, String content,
                     UUID referenceId, String referenceType) {

        if (processedEventRepository.existsByEventId(eventId)) {
            log.info("[NotificationService] 중복 이벤트 무시. eventId={}", eventId);
            return;
        }

        String slackId = resolveSlackId(userId);

        Notification notification = Notification.create(userId, slackId, type, title, content, referenceId, referenceType);
        notificationRepository.save(notification);
        processedEventRepository.save(ProcessedEvent.create(eventId, topic));

        sendToSlack(notification);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getMyNotifications(UUID userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(NotificationResponse::from);
    }

    @Transactional
    public void markAsRead(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(NotificationNotFoundException::new);

        if (!notification.getUserId().equals(userId)) {
            throw new BusinessException(NotificationErrorCode.NOTIFICATION_ACCESS_DENIED);
        }

        notification.markRead();
    }

    @Transactional
    public void retry(Notification notification) {
        sendToSlack(notification);
    }

    private String resolveSlackId(UUID userId) {
        try {
            UserServiceClient.UserSlackResponse data = userServiceClient.getSlackId(userId).data();
            return data != null ? data.slackId() : null;
        } catch (Exception e) {
            log.warn("[NotificationService] 슬랙 ID 조회 실패. userId={}, error={}", userId, e.getMessage());
            return null;
        }
    }

    private void sendToSlack(Notification notification) {
        try {
            slackClient.sendMessage(notification.getSlackId(), formatMessage(notification));
            notification.markSuccess();
            log.info("[NotificationService] 알림 전송 완료. notificationId={}", notification.getNotificationId());
        } catch (Exception e) {
            notification.markFailed();
            log.error("[NotificationService] 알림 전송 실패. notificationId={}, error={}",
                    notification.getNotificationId(), e.getMessage());
        }
    }

    private String formatMessage(Notification notification) {
        return String.format("[%s] %s\n%s", notification.getNotificationType().name(),
                notification.getTitle(), notification.getContent());
    }
}
