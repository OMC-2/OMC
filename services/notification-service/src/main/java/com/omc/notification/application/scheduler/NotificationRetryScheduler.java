package com.omc.notification.application.scheduler;

import com.omc.notification.domain.entity.Notification;
import com.omc.notification.domain.enums.NotificationStatus;
import com.omc.notification.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRetryScheduler {

    private final NotificationRepository notificationRepository;

    // FAILED → PENDING으로 되돌려 NotificationDispatchScheduler가 재처리하도록 함
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void retryFailedNotifications() {
        List<Notification> failed = notificationRepository
                .findTop50ByStatusOrderByCreatedAtAsc(NotificationStatus.FAILED);

        if (failed.isEmpty()) return;

        log.info("[NotificationRetryScheduler] 재시도 대상 {}건", failed.size());

        for (Notification notification : failed) {
            if (!notification.isRetryable()) {
                log.warn("[NotificationRetryScheduler] 최대 재시도 초과 — 포기. notificationId={}", notification.getNotificationId());
                continue;
            }
            notification.resetToPending();
        }
    }
}
