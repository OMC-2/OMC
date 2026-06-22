package com.omc.drop.application.service;

import com.omc.drop.application.event.outbox.PurchaseOutboxWorker;
import com.omc.drop.domain.exception.DuplicatePurchaseException;
import com.omc.drop.domain.exception.DropNotFoundException;
import com.omc.drop.domain.exception.DropNotOpenException;
import com.omc.drop.domain.exception.SoldOutException;
import com.omc.drop.infrastructure.kafka.event.PurchaseConfirmedEvent;
import com.omc.drop.infrastructure.redis.PurchaseRedisRepository;
import com.omc.drop.presentation.dto.response.PurchaseResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    private PurchaseRedisRepository purchaseRedisRepository;

    @Mock
    private PurchaseOutboxWorker outboxWorker;

    @InjectMocks
    private PurchaseService purchaseService;

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final int HOLD_TTL_SEC = 600;

    @Nested
    @DisplayName("선착순 구매 선점")
    class Purchase {

        @Test
        @DisplayName("선점 성공 시 orderId와 queueNumber를 반환한다")
        void returnsPurchaseResponse() {
            UUID dropId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(purchaseRedisRepository.isOpen(dropId)).thenReturn(true);
            when(purchaseRedisRepository.getHoldTtlSec(dropId)).thenReturn(HOLD_TTL_SEC);
            when(purchaseRedisRepository.getProductId(dropId)).thenReturn(PRODUCT_ID);
            when(purchaseRedisRepository.executePurchase(eq(dropId), eq(userId), any(UUID.class), eq(HOLD_TTL_SEC)))
                    .thenReturn(42L);

            PurchaseResponse response = purchaseService.purchase(dropId, userId);

            assertThat(response.orderId()).isNotNull();
            assertThat(response.queueNumber()).isEqualTo(42L);
        }

        @Test
        @DisplayName("Redis OPEN 플래그가 없으면 Lua 실행 없이 DropNotOpenException이 발생한다")
        void throwsDropNotOpenExceptionWithoutLua() {
            UUID dropId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(purchaseRedisRepository.isOpen(dropId)).thenReturn(false);

            assertThatThrownBy(() -> purchaseService.purchase(dropId, userId))
                    .isInstanceOf(DropNotOpenException.class);

            verify(purchaseRedisRepository, never()).executePurchase(any(), any(), any(), anyInt());
        }

        @Test
        @DisplayName("Redis에 productId가 없으면 DropNotFoundException이 발생한다")
        void throwsWhenProductIdNotInRedis() {
            UUID dropId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(purchaseRedisRepository.isOpen(dropId)).thenReturn(true);
            when(purchaseRedisRepository.getHoldTtlSec(dropId)).thenReturn(HOLD_TTL_SEC);
            when(purchaseRedisRepository.getProductId(dropId)).thenReturn(null);

            assertThatThrownBy(() -> purchaseService.purchase(dropId, userId))
                    .isInstanceOf(DropNotFoundException.class);

            verify(purchaseRedisRepository, never()).executePurchase(any(), any(), any(), anyInt());
        }

        @Test
        @DisplayName("Lua 반환 -1이면 SoldOutException이 발생하고 아웃박스에 적재하지 않는다")
        void throwsSoldOutException() {
            UUID dropId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(purchaseRedisRepository.isOpen(dropId)).thenReturn(true);
            when(purchaseRedisRepository.getHoldTtlSec(dropId)).thenReturn(HOLD_TTL_SEC);
            when(purchaseRedisRepository.getProductId(dropId)).thenReturn(PRODUCT_ID);
            when(purchaseRedisRepository.executePurchase(any(), any(), any(), anyInt())).thenReturn(-1L);

            assertThatThrownBy(() -> purchaseService.purchase(dropId, userId))
                    .isInstanceOf(SoldOutException.class);

            verify(outboxWorker, never()).enqueue(any());
        }

        @Test
        @DisplayName("Lua 반환 -2이면 DuplicatePurchaseException이 발생하고 아웃박스에 적재하지 않는다")
        void throwsDuplicatePurchaseException() {
            UUID dropId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(purchaseRedisRepository.isOpen(dropId)).thenReturn(true);
            when(purchaseRedisRepository.getHoldTtlSec(dropId)).thenReturn(HOLD_TTL_SEC);
            when(purchaseRedisRepository.getProductId(dropId)).thenReturn(PRODUCT_ID);
            when(purchaseRedisRepository.executePurchase(any(), any(), any(), anyInt())).thenReturn(-2L);

            assertThatThrownBy(() -> purchaseService.purchase(dropId, userId))
                    .isInstanceOf(DuplicatePurchaseException.class);

            verify(outboxWorker, never()).enqueue(any());
        }

        @Test
        @DisplayName("Lua 반환이 null이면 DuplicatePurchaseException이 발생한다")
        void throwsDuplicatePurchaseExceptionWhenResultIsNull() {
            UUID dropId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(purchaseRedisRepository.isOpen(dropId)).thenReturn(true);
            when(purchaseRedisRepository.getHoldTtlSec(dropId)).thenReturn(HOLD_TTL_SEC);
            when(purchaseRedisRepository.getProductId(dropId)).thenReturn(PRODUCT_ID);
            when(purchaseRedisRepository.executePurchase(any(), any(), any(), anyInt())).thenReturn(null);

            assertThatThrownBy(() -> purchaseService.purchase(dropId, userId))
                    .isInstanceOf(DuplicatePurchaseException.class);
        }

        @Test
        @DisplayName("선점 성공 시 PurchaseConfirmedEvent가 아웃박스에 적재된다")
        void enqueuesEventWithCorrectFields() {
            UUID dropId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(purchaseRedisRepository.isOpen(dropId)).thenReturn(true);
            when(purchaseRedisRepository.getHoldTtlSec(dropId)).thenReturn(HOLD_TTL_SEC);
            when(purchaseRedisRepository.getProductId(dropId)).thenReturn(PRODUCT_ID);
            when(purchaseRedisRepository.executePurchase(any(), any(), any(), anyInt())).thenReturn(1L);

            purchaseService.purchase(dropId, userId);

            ArgumentCaptor<PurchaseConfirmedEvent> captor = ArgumentCaptor.forClass(PurchaseConfirmedEvent.class);
            verify(outboxWorker).enqueue(captor.capture());

            PurchaseConfirmedEvent event = captor.getValue();
            assertThat(event.dropId()).isEqualTo(dropId);
            assertThat(event.userId()).isEqualTo(userId);
            assertThat(event.productId()).isEqualTo(PRODUCT_ID);
            assertThat(event.orderId()).isNotNull();
            assertThat(event.holdExpiresAt()).isNotNull();
        }
    }
}
