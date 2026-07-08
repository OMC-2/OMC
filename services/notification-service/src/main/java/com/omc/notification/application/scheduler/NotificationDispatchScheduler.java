package com.omc.notification.application.scheduler;

import com.omc.notification.domain.entity.Notification;
import com.omc.notification.domain.enums.NotificationStatus;
import com.omc.notification.domain.repository.NotificationRepository;
import com.omc.notification.infrastructure.client.SlackClient;
import com.omc.notification.infrastructure.client.UserServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatchScheduler {

    private final NotificationRepository notificationRepository;
    private final UserServiceClient userServiceClient;
    private final SlackClient slackClient;

    @Scheduled(fixedDelayString = "${notification.dispatch.fixed-delay-ms:10000}")
    @Transactional
    public void dispatchPending() {
        List<Notification> pending = notificationRepository
                .findTop100ByStatusOrderByCreatedAtAsc(NotificationStatus.PENDING);
        if (pending.isEmpty()) return;

        List<UUID> userIds = pending.stream()
                .map(Notification::getUserId)
                .distinct()
                .toList();

        Map<UUID, String> slackIdMap = fetchSlackIdsBatch(userIds);

        for (Notification notification : pending) {
            String slackId = slackIdMap.get(notification.getUserId());
            if (slackId == null || slackId.isBlank()) {
                notification.markFailed();
                continue;
            }
            notification.updateSlackId(slackId);
            try {
                slackClient.sendMessage(slackId, formatMessage(notification));
                notification.markSuccess();
            } catch (Exception e) {
                log.error("[NotificationDispatchScheduler] Slack 전송 실패. notificationId={}", notification.getNotificationId(), e);
                notification.markFailed();
            }
        }
    }

    private Map<UUID, String> fetchSlackIdsBatch(List<UUID> userIds) {
        try {
            List<UserServiceClient.UserSlackResponse> responses =
                    userServiceClient.getSlackIdsBatch(userIds).data();
            return responses.stream()
                    .filter(r -> r.slackId() != null)
                    .collect(Collectors.toMap(UserServiceClient.UserSlackResponse::userId,
                                              UserServiceClient.UserSlackResponse::slackId));
        } catch (Exception e) {
            log.error("[NotificationDispatchScheduler] 배치 슬랙 ID 조회 실패", e);
            return Map.of();
        }
    }

    private String formatMessage(Notification notification) {
        return String.format("[%s] %s\n%s",
                notification.getNotificationType().name(),
                notification.getTitle(),
                notification.getContent());
    }
}
