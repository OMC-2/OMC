package com.omc.drop.application.scheduler;

import com.omc.drop.application.event.producer.DropEventProducer;
import com.omc.drop.domain.entity.Drop;
import com.omc.drop.domain.enums.DropStatus;
import com.omc.drop.domain.repository.DropRepository;
import com.omc.drop.application.event.producer.HoldExpiredEvent;
import com.omc.drop.infrastructure.redis.PurchaseRedisRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HoldExpireScheduler 테스트")
class HoldExpireSchedulerTest {

    @Mock
    private DropRepository dropRepository;

    @Mock
    private PurchaseRedisRepository purchaseRedisRepository;

    @Mock
    private DropEventProducer dropEventProducer;

    @InjectMocks
    private HoldExpireScheduler holdExpireScheduler;

    @Nested
    @DisplayName("hold 만료 처리")
    class ExpireHolds {

        @Test
        @DisplayName("만료된 orderId가 없으면 아무 처리도 하지 않는다")
        void doesNothingWhenNoExpiredHolds() {
            Drop drop = createOpenDrop();
            when(dropRepository.findByStatus(DropStatus.OPEN)).thenReturn(List.of(drop));
            when(purchaseRedisRepository.getExpiredOrderIds(eq(drop.getDropId()), anyLong()))
                    .thenReturn(Set.of());

            holdExpireScheduler.expireHolds();

            verify(purchaseRedisRepository, never()).expireHold(any(), any());
            verify(dropEventProducer, never()).publishHoldExpired(any());
        }

        @Test
        @DisplayName("OPEN 드롭이 없으면 아무 처리도 하지 않는다")
        void doesNothingWhenNoOpenDrops() {
            when(dropRepository.findByStatus(DropStatus.OPEN)).thenReturn(List.of());

            holdExpireScheduler.expireHolds();

            verify(purchaseRedisRepository, never()).getExpiredOrderIds(any(), anyLong());
            verify(dropEventProducer, never()).publishHoldExpired(any());
        }

        @Test
        @DisplayName("expire.lua 반환 1 → hold.expired 발행")
        void publishesHoldExpiredWhenExpireLuaReturnsOne() {
            Drop drop = createOpenDrop();
            UUID orderId = UUID.randomUUID();
            when(dropRepository.findByStatus(DropStatus.OPEN)).thenReturn(List.of(drop));
            when(purchaseRedisRepository.getExpiredOrderIds(eq(drop.getDropId()), anyLong()))
                    .thenReturn(Set.of(orderId.toString()));
            when(purchaseRedisRepository.expireHold(drop.getDropId(), orderId)).thenReturn(1L);

            holdExpireScheduler.expireHolds();

            ArgumentCaptor<HoldExpiredEvent> captor = ArgumentCaptor.forClass(HoldExpiredEvent.class);
            verify(dropEventProducer).publishHoldExpired(captor.capture());
            assertThat(captor.getValue().orderId()).isEqualTo(orderId);
            assertThat(captor.getValue().dropId()).isEqualTo(drop.getDropId());
        }

        @Test
        @DisplayName("expire.lua 반환 0 → 다른 인스턴스가 먼저 처리, hold.expired 미발행")
        void skipsWhenExpireLuaReturnsZero() {
            Drop drop = createOpenDrop();
            UUID orderId = UUID.randomUUID();
            when(dropRepository.findByStatus(DropStatus.OPEN)).thenReturn(List.of(drop));
            when(purchaseRedisRepository.getExpiredOrderIds(eq(drop.getDropId()), anyLong()))
                    .thenReturn(Set.of(orderId.toString()));
            when(purchaseRedisRepository.expireHold(drop.getDropId(), orderId)).thenReturn(0L);

            holdExpireScheduler.expireHolds();

            verify(dropEventProducer, never()).publishHoldExpired(any());
        }

        @Test
        @DisplayName("하나의 orderId 처리 실패 시 나머지 orderId는 계속 처리한다")
        void continuesProcessingOnPartialFailure() {
            Drop drop = createOpenDrop();
            UUID failOrderId = UUID.randomUUID();
            UUID successOrderId = UUID.randomUUID();
            when(dropRepository.findByStatus(DropStatus.OPEN)).thenReturn(List.of(drop));
            when(purchaseRedisRepository.getExpiredOrderIds(eq(drop.getDropId()), anyLong()))
                    .thenReturn(Set.of(failOrderId.toString(), successOrderId.toString()));
            when(purchaseRedisRepository.expireHold(drop.getDropId(), failOrderId))
                    .thenThrow(new RuntimeException("Redis 오류"));
            when(purchaseRedisRepository.expireHold(drop.getDropId(), successOrderId)).thenReturn(1L);

            holdExpireScheduler.expireHolds();

            verify(dropEventProducer).publishHoldExpired(argThat(e -> e.orderId().equals(successOrderId)));
        }
    }

    private Drop createOpenDrop() {
        LocalDateTime startAt = LocalDateTime.now().minusDays(1);
        Drop drop = Drop.create(UUID.randomUUID(), startAt, startAt.plusDays(2), 100, 600);
        ReflectionTestUtils.setField(drop, "dropId", UUID.randomUUID());
        drop.open();
        return drop;
    }
}
