package com.omc.drop.application.service;

import com.omc.drop.domain.exception.DuplicatePurchaseException;
import com.omc.drop.domain.exception.DropNotFoundException;
import com.omc.drop.domain.exception.DropNotOpenException;
import com.omc.drop.domain.exception.PurchaseReservationFailedException;
import com.omc.drop.domain.exception.SoldOutException;
import com.omc.drop.infrastructure.metrics.DropMetrics;
import com.omc.drop.infrastructure.redis.DropRedisStore;
import com.omc.drop.presentation.dto.response.PurchaseResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PurchaseService 테스트")
class PurchaseServiceTest {

    @Mock
    private DropRedisStore dropRedisStore;

    @Mock
    private DropPurchaseReservationService reservationService;

    @Mock
    private DropMetrics dropMetrics;

    @InjectMocks
    private PurchaseService purchaseService;

    @Nested
    @DisplayName("선착순 구매 선점")
    class Purchase {

        @Test
        @DisplayName("선점 성공 시 orderId와 queueNumber를 반환한다")
        void returnsPurchaseResponse() {
            UUID dropId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(dropRedisStore.executePurchase(eq(dropId), eq(userId), any(UUID.class))).thenReturn(42L);
            when(dropRedisStore.getHoldExpiresAtEpochSec(eq(dropId), any(UUID.class))).thenReturn(9999999999L);
            when(dropRedisStore.getProductId(dropId)).thenReturn(UUID.randomUUID());

            PurchaseResponse response = purchaseService.purchase(dropId, userId);

            assertThat(response.orderId()).isNotNull();
            assertThat(response.queueNumber()).isEqualTo(42L);
        }

        @Test
        @DisplayName("선점 성공 시 reservationService.record()가 호출된다")
        void callsOutboxRecorderOnSuccess() {
            UUID dropId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();

            when(dropRedisStore.executePurchase(any(), any(), any(UUID.class))).thenReturn(1L);
            when(dropRedisStore.getHoldExpiresAtEpochSec(any(), any())).thenReturn(9999999999L);
            when(dropRedisStore.getProductId(dropId)).thenReturn(productId);

            purchaseService.purchase(dropId, userId);

            verify(reservationService).record(
                    eq(dropId), eq(userId), any(UUID.class),
                    eq(productId), eq(9999999999L), eq(1L));
        }

        @Test
        @DisplayName("Lua 반환 -3이면 DropNotOpenException이 발생한다")
        void throwsDropNotOpenException() {
            UUID dropId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(dropRedisStore.executePurchase(any(), any(), any(UUID.class))).thenReturn(-3L);

            assertThatThrownBy(() -> purchaseService.purchase(dropId, userId))
                    .isInstanceOf(DropNotOpenException.class);

            verifyNoInteractions(reservationService);
        }

        @Test
        @DisplayName("Lua 반환 null이면 DropNotOpenException이 발생한다")
        void throwsDropNotOpenExceptionWhenNull() {
            UUID dropId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(dropRedisStore.executePurchase(any(), any(), any(UUID.class))).thenReturn(null);

            assertThatThrownBy(() -> purchaseService.purchase(dropId, userId))
                    .isInstanceOf(DropNotOpenException.class);

            verifyNoInteractions(reservationService);
        }

        @Test
        @DisplayName("Lua 반환 -4이면 DropNotFoundException이 발생한다")
        void throwsDropNotFoundException() {
            UUID dropId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(dropRedisStore.executePurchase(any(), any(), any(UUID.class))).thenReturn(-4L);

            assertThatThrownBy(() -> purchaseService.purchase(dropId, userId))
                    .isInstanceOf(DropNotFoundException.class);

            verifyNoInteractions(reservationService);
        }

        @Test
        @DisplayName("Lua 반환 -1이면 SoldOutException이 발생한다")
        void throwsSoldOutException() {
            UUID dropId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(dropRedisStore.executePurchase(any(), any(), any(UUID.class))).thenReturn(-1L);

            assertThatThrownBy(() -> purchaseService.purchase(dropId, userId))
                    .isInstanceOf(SoldOutException.class);

            verifyNoInteractions(reservationService);
        }

        @Test
        @DisplayName("Lua 반환 -2이면 DuplicatePurchaseException이 발생한다")
        void throwsDuplicatePurchaseException() {
            UUID dropId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(dropRedisStore.executePurchase(any(), any(), any(UUID.class))).thenReturn(-2L);

            assertThatThrownBy(() -> purchaseService.purchase(dropId, userId))
                    .isInstanceOf(DuplicatePurchaseException.class);

            verifyNoInteractions(reservationService);
        }

        @Test
        @DisplayName("선점 성공 시 executePurchase에 dropId·userId가 올바르게 전달된다")
        void passesCorrectArgumentsToExecutePurchase() {
            UUID dropId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(dropRedisStore.executePurchase(any(), any(), any(UUID.class))).thenReturn(1L);
            when(dropRedisStore.getHoldExpiresAtEpochSec(any(), any())).thenReturn(9999999999L);
            when(dropRedisStore.getProductId(any())).thenReturn(UUID.randomUUID());

            purchaseService.purchase(dropId, userId);

            verify(dropRedisStore).executePurchase(eq(dropId), eq(userId), any(UUID.class));
        }

        @Test
        @DisplayName("DB 저장 실패 시 rollbackPurchaseClaim을 호출하고 PurchaseReservationFailedException을 던진다")
        void rollsBackRedisOnDbFailure() {
            UUID dropId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(dropRedisStore.executePurchase(any(), any(), any(UUID.class))).thenReturn(1L);
            when(dropRedisStore.getHoldExpiresAtEpochSec(any(), any())).thenReturn(9999999999L);
            when(dropRedisStore.getProductId(any())).thenReturn(UUID.randomUUID());
            doThrow(new RuntimeException("DB 연결 실패")).when(reservationService)
                    .record(any(), any(), any(), any(), anyLong(), anyLong());

            assertThatThrownBy(() -> purchaseService.purchase(dropId, userId))
                    .isInstanceOf(PurchaseReservationFailedException.class);

            verify(dropRedisStore).rollbackPurchaseClaim(eq(dropId), any(UUID.class), eq(userId));
        }

        @Test
        @DisplayName("DB 저장 실패 시 reservationService는 한 번만 호출된다")
        void dbFailureDoesNotRetry() {
            UUID dropId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(dropRedisStore.executePurchase(any(), any(), any(UUID.class))).thenReturn(1L);
            when(dropRedisStore.getHoldExpiresAtEpochSec(any(), any())).thenReturn(9999999999L);
            when(dropRedisStore.getProductId(any())).thenReturn(UUID.randomUUID());
            doThrow(new RuntimeException("DB 오류")).when(reservationService)
                    .record(any(), any(), any(), any(), anyLong(), anyLong());

            assertThatThrownBy(() -> purchaseService.purchase(dropId, userId))
                    .isInstanceOf(PurchaseReservationFailedException.class);

            verify(reservationService, times(1)).record(any(), any(), any(), any(), anyLong(), anyLong());
        }

        @Test
        @DisplayName("rollbackPurchaseClaim이 처음엔 실패하다가 재시도 중 성공하면 3회 이내 종료된다")
        void retriesRollbackUntilSuccess() {
            UUID dropId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(dropRedisStore.executePurchase(any(), any(), any(UUID.class))).thenReturn(1L);
            when(dropRedisStore.getHoldExpiresAtEpochSec(any(), any())).thenReturn(9999999999L);
            when(dropRedisStore.getProductId(any())).thenReturn(UUID.randomUUID());
            doThrow(new RuntimeException("DB 오류")).when(reservationService)
                    .record(any(), any(), any(), any(), anyLong(), anyLong());
            when(dropRedisStore.rollbackPurchaseClaim(any(), any(), any()))
                    .thenThrow(new RuntimeException("Redis 일시 오류"))
                    .thenReturn(1L); // 2번째 시도 성공

            assertThatThrownBy(() -> purchaseService.purchase(dropId, userId))
                    .isInstanceOf(PurchaseReservationFailedException.class);

            verify(dropRedisStore, times(2)).rollbackPurchaseClaim(any(), any(), any());
            verify(dropMetrics, never()).incrementRedisCompensationFailed(any());
        }

        @Test
        @DisplayName("rollbackPurchaseClaim이 0을 반환하면 (hold 이미 만료) 1회만 호출하고 메트릭을 증가시키지 않는다")
        void skipsMetricWhenHoldAlreadyGone() {
            UUID dropId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(dropRedisStore.executePurchase(any(), any(), any(UUID.class))).thenReturn(1L);
            when(dropRedisStore.getHoldExpiresAtEpochSec(any(), any())).thenReturn(9999999999L);
            when(dropRedisStore.getProductId(any())).thenReturn(UUID.randomUUID());
            doThrow(new RuntimeException("DB 오류")).when(reservationService)
                    .record(any(), any(), any(), any(), anyLong(), anyLong());
            when(dropRedisStore.rollbackPurchaseClaim(any(), any(), any())).thenReturn(0L);

            assertThatThrownBy(() -> purchaseService.purchase(dropId, userId))
                    .isInstanceOf(PurchaseReservationFailedException.class);

            verify(dropRedisStore, times(1)).rollbackPurchaseClaim(any(), any(), any());
            verify(dropMetrics, never()).incrementRedisCompensationFailed(any());
        }

        @Test
        @DisplayName("rollbackPurchaseClaim이 3회 모두 실패하면 CRITICAL 메트릭을 증가시킨다")
        void incrementsCompensationMetricAfterAllRetriesFail() {
            UUID dropId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(dropRedisStore.executePurchase(any(), any(), any(UUID.class))).thenReturn(1L);
            when(dropRedisStore.getHoldExpiresAtEpochSec(any(), any())).thenReturn(9999999999L);
            when(dropRedisStore.getProductId(any())).thenReturn(UUID.randomUUID());
            doThrow(new RuntimeException("DB 오류")).when(reservationService)
                    .record(any(), any(), any(), any(), anyLong(), anyLong());
            when(dropRedisStore.rollbackPurchaseClaim(any(), any(), any()))
                    .thenThrow(new RuntimeException("Redis 연결 불가"));

            assertThatThrownBy(() -> purchaseService.purchase(dropId, userId))
                    .isInstanceOf(PurchaseReservationFailedException.class);

            verify(dropRedisStore, times(3)).rollbackPurchaseClaim(any(), any(), any());
            verify(dropMetrics, times(1)).incrementRedisCompensationFailed(eq(dropId));
        }
    }
}
