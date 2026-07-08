package com.omc.drop.application.service;

import com.omc.drop.domain.exception.DuplicatePurchaseException;
import com.omc.drop.domain.exception.DropNotFoundException;
import com.omc.drop.domain.exception.DropNotOpenException;
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

            when(dropRedisStore.executePurchase(eq(dropId), eq(userId), any(UUID.class), anyString()))
                    .thenReturn(42L);

            PurchaseResponse response = purchaseService.purchase(dropId, userId);

            assertThat(response.orderId()).isNotNull();
            assertThat(response.queueNumber()).isEqualTo(42L);
        }

        @Test
        @DisplayName("Lua 반환 -3이면 DropNotOpenException이 발생한다")
        void throwsDropNotOpenException() {
            UUID dropId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(dropRedisStore.executePurchase(any(), any(), any(UUID.class), anyString()))
                    .thenReturn(-3L);

            assertThatThrownBy(() -> purchaseService.purchase(dropId, userId))
                    .isInstanceOf(DropNotOpenException.class);
        }

        @Test
        @DisplayName("Lua 반환 null이면 DropNotOpenException이 발생한다")
        void throwsDropNotOpenExceptionWhenNull() {
            UUID dropId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(dropRedisStore.executePurchase(any(), any(), any(UUID.class), anyString()))
                    .thenReturn(null);

            assertThatThrownBy(() -> purchaseService.purchase(dropId, userId))
                    .isInstanceOf(DropNotOpenException.class);
        }

        @Test
        @DisplayName("Lua 반환 -4이면 DropNotFoundException이 발생한다")
        void throwsDropNotFoundException() {
            UUID dropId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(dropRedisStore.executePurchase(any(), any(), any(UUID.class), anyString()))
                    .thenReturn(-4L);

            assertThatThrownBy(() -> purchaseService.purchase(dropId, userId))
                    .isInstanceOf(DropNotFoundException.class);
        }

        @Test
        @DisplayName("Lua 반환 -1이면 SoldOutException이 발생한다")
        void throwsSoldOutException() {
            UUID dropId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(dropRedisStore.executePurchase(any(), any(), any(UUID.class), anyString()))
                    .thenReturn(-1L);

            assertThatThrownBy(() -> purchaseService.purchase(dropId, userId))
                    .isInstanceOf(SoldOutException.class);
        }

        @Test
        @DisplayName("Lua 반환 -2이면 DuplicatePurchaseException이 발생한다")
        void throwsDuplicatePurchaseException() {
            UUID dropId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(dropRedisStore.executePurchase(any(), any(), any(UUID.class), anyString()))
                    .thenReturn(-2L);

            assertThatThrownBy(() -> purchaseService.purchase(dropId, userId))
                    .isInstanceOf(DuplicatePurchaseException.class);
        }

        @Test
        @DisplayName("선점 성공 시 executePurchase에 dropId·userId가 올바르게 전달된다")
        void passesCorrectArgumentsToExecutePurchase() {
            UUID dropId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(dropRedisStore.executePurchase(any(), any(), any(UUID.class), anyString()))
                    .thenReturn(1L);

            purchaseService.purchase(dropId, userId);

            verify(dropRedisStore).executePurchase(eq(dropId), eq(userId), any(UUID.class), anyString());
        }
    }
}
