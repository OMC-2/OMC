package com.omc.notification.unit.entity;

import com.omc.notification.domain.entity.Notification;
import com.omc.notification.domain.enums.NotificationType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationEntityUuidV7Test {

    // =========================================================================
    // [1] Notification.create() → notificationId version 7
    // =========================================================================

    @Test
    void create_notificationId_isVersion7() {
        Notification notification = notification();

        assertThat(notification.getNotificationId().version()).isEqualTo(7);
    }

    // =========================================================================
    // [2] 연속 생성한 Notification ID가 시간순으로 정렬되는지 확인
    // =========================================================================

    @Test
    void create_multipleNotifications_idsAreSortedByCreationOrder() throws InterruptedException {
        Notification first = notification();
        Thread.sleep(1);
        Notification second = notification();

        assertThat(first.getNotificationId().toString()).isLessThan(second.getNotificationId().toString());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Notification notification() {
        return Notification.create(
                UUID.randomUUID(), "U001", NotificationType.COUPON_ISSUED,
                "테스트 알림", "내용", null, null);
    }
}
