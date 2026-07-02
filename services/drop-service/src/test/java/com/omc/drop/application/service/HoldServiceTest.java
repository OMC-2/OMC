package com.omc.drop.application.service;

import com.omc.drop.application.event.producer.DropEventProducer;
import com.omc.drop.application.event.producer.RefundRequestedEvent;
import com.omc.drop.infrastructure.metrics.DropMetrics;
import com.omc.drop.infrastructure.redis.DropRedisStore;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HoldService 테스트")
class HoldServiceTest {

    @Mock
    private DropRedisStore dropRedisStore;

    @Mock
    private DropEventProducer dropEventProducer;

    @Mock
    private DropMetrics dropMetrics;

    @InjectMocks
    private HoldService holdService;

    @Nested
    @DisplayName("hold 확정 (confirmHold)")
    class ConfirmHold {

        @Test
        @DisplayName("ZREM 반환 1 → 정상 확정, refund.requested 미발행")
        void normalConfirm() {
            UUID dropId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            when(dropRedisStore.removeHold(dropId, orderId)).thenReturn(1L);

            holdService.confirmHold(dropId, orderId, userId);

            verify(dropEventProducer, never()).publishRefundRequested(any());
        }

        @Test
        @DisplayName("ZREM 반환 0 → LATE_PAYMENT, refund.requested 발행")
        void latePaymentPublishesRefund() {
            UUID dropId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            when(dropRedisStore.removeHold(dropId, orderId)).thenReturn(0L);

            holdService.confirmHold(dropId, orderId, userId);

            ArgumentCaptor<RefundRequestedEvent> captor = ArgumentCaptor.forClass(RefundRequestedEvent.class);
            verify(dropEventProducer).publishRefundRequested(captor.capture());
            assertThat(captor.getValue().orderId()).isEqualTo(orderId);
            assertThat(captor.getValue().userId()).isEqualTo(userId);
            assertThat(captor.getValue().reason()).isEqualTo("LATE_PAYMENT");
        }
    }

    @Nested
    @DisplayName("hold 복구 (recoverHold)")
    class RecoverHold {

        @Test
        @DisplayName("Lua 반환 1 → 재고·구매이력 복구 완료")
        void normalRecovery() {
            UUID dropId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            when(dropRedisStore.recoverStock(dropId, orderId, userId)).thenReturn(1L);

            holdService.recoverHold(dropId, orderId, userId);

            verify(dropRedisStore).recoverStock(dropId, orderId, userId);
        }

        @Test
        @DisplayName("Lua 반환 0 → 이미 처리된 hold, recoverStock 호출 후 no-op")
        void alreadyProcessedIsNoOp() {
            UUID dropId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            when(dropRedisStore.recoverStock(dropId, orderId, userId)).thenReturn(0L);

            holdService.recoverHold(dropId, orderId, userId);

            verify(dropRedisStore).recoverStock(dropId, orderId, userId);
            verify(dropEventProducer, never()).publishRefundRequested(any());
        }
    }
}
