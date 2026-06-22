package com.omc.notification.unit.service;

import com.omc.common.exception.BusinessException;
import com.omc.notification.application.service.NotificationService;
import com.omc.notification.domain.entity.Notification;
import com.omc.notification.domain.enums.NotificationStatus;
import com.omc.notification.domain.enums.NotificationType;
import com.omc.notification.domain.exception.NotificationErrorCode;
import com.omc.notification.domain.exception.NotificationNotFoundException;
import com.omc.notification.domain.repository.NotificationRepository;
import com.omc.notification.domain.repository.ProcessedEventRepository;
import com.omc.notification.infrastructure.client.SlackClient;
import com.omc.notification.infrastructure.client.UserServiceClient;
import com.omc.notification.presentation.dto.response.NotificationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private ProcessedEventRepository processedEventRepository;
    @Mock private UserServiceClient userServiceClient;
    @Mock private SlackClient slackClient;

    @InjectMocks private NotificationService notificationService;

    private final UUID userId = UUID.randomUUID();
    private final UUID notificationId = UUID.randomUUID();
    private final String eventId = "event-001";
    private final String topic = "order.confirmed";

    // =========================================================================
    // [send()] 정상 전송
    // =========================================================================

    @Test
    void send_success() {
        // given
        given(processedEventRepository.existsByEventId(eventId)).willReturn(false);
        given(userServiceClient.getSlackId(userId))
                .willReturn(new UserServiceClient.SlackApiResponse(true, 200, "OK",
                        new UserServiceClient.UserSlackResponse(userId, "U12345")));

        // when
        notificationService.send(eventId, topic, userId,
                NotificationType.ORDER_CONFIRMED, "주문 확정", "주문이 확정됐습니다.", null, null);

        // then
        verify(notificationRepository).save(any(Notification.class));
        verify(processedEventRepository).save(any());
        verify(slackClient).sendMessage(eq("U12345"), any());
    }

    // =========================================================================
    // [send()] 중복 이벤트 무시
    // =========================================================================

    @Test
    void send_duplicateEvent_ignored() {
        // given
        given(processedEventRepository.existsByEventId(eventId)).willReturn(true);

        // when
        notificationService.send(eventId, topic, userId,
                NotificationType.ORDER_CONFIRMED, "주문 확정", "주문이 확정됐습니다.", null, null);

        // then
        verify(notificationRepository, never()).save(any());
        verify(slackClient, never()).sendMessage(any(), any());
    }

    // =========================================================================
    // [send()] slackId 조회 실패 → null (전송 스킵)
    // =========================================================================

    @Test
    void send_slackIdResolveFails_savesWithNullSlackId() {
        // given
        given(processedEventRepository.existsByEventId(eventId)).willReturn(false);
        given(userServiceClient.getSlackId(userId)).willThrow(new RuntimeException("Feign 오류"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        // when
        notificationService.send(eventId, topic, userId,
                NotificationType.ORDER_CONFIRMED, "주문 확정", "주문이 확정됐습니다.", null, null);

        // then
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getSlackId()).isNull();
    }

    // =========================================================================
    // [send()] 슬랙 전송 실패 → status = FAILED
    // =========================================================================

    @Test
    void send_slackSendFails_marksNotificationFailed() {
        // given
        given(processedEventRepository.existsByEventId(eventId)).willReturn(false);
        given(userServiceClient.getSlackId(userId))
                .willReturn(new UserServiceClient.SlackApiResponse(true, 200, "OK",
                        new UserServiceClient.UserSlackResponse(userId, "U12345")));
        willThrow(new RuntimeException("Slack 오류")).given(slackClient).sendMessage(any(), any());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        // when
        notificationService.send(eventId, topic, userId,
                NotificationType.ORDER_CONFIRMED, "주문 확정", "주문이 확정됐습니다.", null, null);

        // then
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    // =========================================================================
    // [getMyNotifications()] 페이지 조회 위임
    // =========================================================================

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void getMyNotifications_success() {
        // given
        Pageable pageable = mock(Pageable.class);
        Page<Notification> page = mock(Page.class);
        Page<NotificationResponse> responsePage = mock(Page.class);
        given(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)).willReturn(page);
        given(page.map(any())).willReturn((Page) responsePage);

        // when
        Page<NotificationResponse> result = notificationService.getMyNotifications(userId, pageable);

        // then
        assertThat(result).isEqualTo(responsePage);
        verify(notificationRepository).findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    // =========================================================================
    // [markAsRead()] 정상 읽음 처리
    // =========================================================================

    @Test
    void markAsRead_success() {
        // given
        Notification notification = Notification.create(
                userId, "U12345", NotificationType.ORDER_CONFIRMED, "주문 확정", "내용", null, null);
        given(notificationRepository.findById(notificationId)).willReturn(Optional.of(notification));

        // when
        notificationService.markAsRead(userId, notificationId);

        // then
        assertThat(notification.isRead()).isTrue();
    }

    // =========================================================================
    // [markAsRead()] 알림 없음 → NotificationNotFoundException
    // =========================================================================

    @Test
    void markAsRead_notFound_throwsException() {
        // given
        given(notificationRepository.findById(notificationId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> notificationService.markAsRead(userId, notificationId))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    // =========================================================================
    // [markAsRead()] 본인 소유 아님 → NOTIFICATION_ACCESS_DENIED
    // =========================================================================

    @Test
    void markAsRead_notOwner_throwsException() {
        // given
        UUID anotherUserId = UUID.randomUUID();
        Notification notification = Notification.create(
                anotherUserId, "U12345", NotificationType.ORDER_CONFIRMED, "주문 확정", "내용", null, null);
        given(notificationRepository.findById(notificationId)).willReturn(Optional.of(notification));

        // when & then
        assertThatThrownBy(() -> notificationService.markAsRead(userId, notificationId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(NotificationErrorCode.NOTIFICATION_ACCESS_DENIED));
    }

    // =========================================================================
    // [retry()] 재전송 성공
    // =========================================================================

    @Test
    void retry_success() {
        // given
        Notification notification = Notification.create(
                userId, "U12345", NotificationType.ORDER_CONFIRMED, "주문 확정", "내용", null, null);

        // when
        notificationService.retry(notification);

        // then
        verify(slackClient).sendMessage(eq("U12345"), any());
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SUCCESS);
    }

    // =========================================================================
    // [retry()] 재전송 실패 → status = FAILED
    // =========================================================================

    @Test
    void retry_fails_marksAsFailed() {
        // given
        Notification notification = Notification.create(
                userId, "U12345", NotificationType.ORDER_CONFIRMED, "주문 확정", "내용", null, null);
        willThrow(new RuntimeException("Slack 오류")).given(slackClient).sendMessage(any(), any());

        // when
        notificationService.retry(notification);

        // then
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
    }
}
