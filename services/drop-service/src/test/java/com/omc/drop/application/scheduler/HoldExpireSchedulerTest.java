package com.omc.drop.application.scheduler;

import com.omc.drop.application.event.producer.DropEventProducer;
import com.omc.drop.domain.entity.Drop;
import com.omc.drop.domain.enums.DropStatus;
import com.omc.drop.domain.repository.DropRepository;
import com.omc.drop.application.event.producer.HoldExpiredEvent;
import com.omc.drop.infrastructure.redis.DropRedisStore;
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

import static com.omc.drop.domain.enums.DropStatus.CLOSED;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HoldExpireScheduler 테스트")
class HoldExpireSchedulerTest {

    @Mock
    private DropRepository dropRepository;

    @Mock
    private DropRedisStore dropRedisStore;

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
            when(dropRedisStore.getOpenDropIds()).thenReturn(Set.of(drop.getDropId().toString()));
            when(dropRedisStore.getExpiredOrderIds(eq(drop.getDropId()), anyLong()))
                    .thenReturn(Set.of());

            holdExpireScheduler.expireHolds();

            verify(dropRedisStore, never()).expireHold(any(), any());
            verify(dropEventProducer, never()).publishHoldExpired(any());
        }

        @Test
        @DisplayName("open_drops Set이 비어있고 DB에도 OPEN 드롭이 없으면 아무 처리도 하지 않는다")
        void doesNothingWhenNoOpenDrops() {
            when(dropRedisStore.getOpenDropIds()).thenReturn(Set.of());
            when(dropRepository.findByStatus(DropStatus.OPEN)).thenReturn(List.of());

            holdExpireScheduler.expireHolds();

            verify(dropRedisStore, never()).getExpiredOrderIds(any(), anyLong());
            verify(dropEventProducer, never()).publishHoldExpired(any());
        }

        @Test
        @DisplayName("expire.lua 반환 1 → hold.expired 발행")
        void publishesHoldExpiredWhenExpireLuaReturnsOne() {
            Drop drop = createOpenDrop();
            UUID orderId = UUID.randomUUID();
            when(dropRedisStore.getOpenDropIds()).thenReturn(Set.of(drop.getDropId().toString()));
            when(dropRedisStore.getExpiredOrderIds(eq(drop.getDropId()), anyLong()))
                    .thenReturn(Set.of(orderId.toString()));
            when(dropRedisStore.expireHold(drop.getDropId(), orderId)).thenReturn(1L);

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
            when(dropRedisStore.getOpenDropIds()).thenReturn(Set.of(drop.getDropId().toString()));
            when(dropRedisStore.getExpiredOrderIds(eq(drop.getDropId()), anyLong()))
                    .thenReturn(Set.of(orderId.toString()));
            when(dropRedisStore.expireHold(drop.getDropId(), orderId)).thenReturn(0L);

            holdExpireScheduler.expireHolds();

            verify(dropEventProducer, never()).publishHoldExpired(any());
        }

        @Test
        @DisplayName("하나의 orderId 처리 실패 시 나머지 orderId는 계속 처리한다")
        void continuesProcessingOnPartialFailure() {
            Drop drop = createOpenDrop();
            UUID failOrderId = UUID.randomUUID();
            UUID successOrderId = UUID.randomUUID();
            when(dropRedisStore.getOpenDropIds()).thenReturn(Set.of(drop.getDropId().toString()));
            when(dropRedisStore.getExpiredOrderIds(eq(drop.getDropId()), anyLong()))
                    .thenReturn(Set.of(failOrderId.toString(), successOrderId.toString()));
            when(dropRedisStore.expireHold(drop.getDropId(), failOrderId))
                    .thenThrow(new RuntimeException("Redis 오류"));
            when(dropRedisStore.expireHold(drop.getDropId(), successOrderId)).thenReturn(1L);

            holdExpireScheduler.expireHolds();

            verify(dropEventProducer).publishHoldExpired(argThat(e -> e.orderId().equals(successOrderId)));
        }
    }

    @Nested
    @DisplayName("open_drops Set DB 복구")
    class RecoverFromDb {

        @Test
        @DisplayName("open_drops Set이 비었으면 DB에서 OPEN 드롭을 조회하고 addOpenDrop을 호출한다")
        void recoversOpenDropsFromDbWhenSetIsEmpty() {
            Drop drop = createOpenDrop();
            when(dropRedisStore.getOpenDropIds()).thenReturn(Set.of());
            when(dropRepository.findByStatus(DropStatus.OPEN)).thenReturn(List.of(drop));
            when(dropRepository.findByStatusAndEndAtGreaterThanEqual(eq(CLOSED), any(LocalDateTime.class)))
                    .thenReturn(List.of());
            when(dropRedisStore.getExpiredOrderIds(eq(drop.getDropId()), anyLong())).thenReturn(Set.of());

            holdExpireScheduler.expireHolds();

            verify(dropRedisStore).addOpenDrop(drop.getDropId());
            verify(dropRedisStore).getExpiredOrderIds(eq(drop.getDropId()), anyLong());
        }

        @Test
        @DisplayName("재시작 시 CLOSED이지만 holds가 남은 드롭도 Set에 복구한다")
        void recoversClosedDropWithRemainingHoldsFromDb() {
            Drop closedDrop = createClosedDrop();
            when(dropRedisStore.getOpenDropIds()).thenReturn(Set.of());
            when(dropRepository.findByStatus(DropStatus.OPEN)).thenReturn(List.of());
            when(dropRepository.findByStatusAndEndAtGreaterThanEqual(eq(CLOSED), any(LocalDateTime.class)))
                    .thenReturn(List.of(closedDrop));
            when(dropRedisStore.isHoldsEmpty(closedDrop.getDropId())).thenReturn(false);
            when(dropRedisStore.getExpiredOrderIds(eq(closedDrop.getDropId()), anyLong())).thenReturn(Set.of());

            holdExpireScheduler.expireHolds();

            verify(dropRedisStore).addOpenDrop(closedDrop.getDropId());
        }

        @Test
        @DisplayName("재시작 시 CLOSED이고 holds도 비었으면 Set에 복구하지 않는다")
        void doesNotRecoverClosedDropWithEmptyHolds() {
            Drop closedDrop = createClosedDrop();
            when(dropRedisStore.getOpenDropIds()).thenReturn(Set.of());
            when(dropRepository.findByStatus(DropStatus.OPEN)).thenReturn(List.of());
            when(dropRepository.findByStatusAndEndAtGreaterThanEqual(eq(CLOSED), any(LocalDateTime.class)))
                    .thenReturn(List.of(closedDrop));
            when(dropRedisStore.isHoldsEmpty(closedDrop.getDropId())).thenReturn(true);

            holdExpireScheduler.expireHolds();

            verify(dropRedisStore, never()).addOpenDrop(closedDrop.getDropId());
        }
    }

    @Nested
    @DisplayName("CLOSED 드롭 Set 정리")
    class ClosedDropCleanup {

        @Test
        @DisplayName("CLOSED 드롭이고 holds가 비었으면 open_drops Set에서 제거한다")
        void removesClosedDropFromSetWhenHoldsEmpty() {
            UUID dropId = UUID.randomUUID();
            when(dropRedisStore.getOpenDropIds()).thenReturn(Set.of(dropId.toString()));
            when(dropRedisStore.getExpiredOrderIds(eq(dropId), anyLong())).thenReturn(Set.of());
            when(dropRedisStore.isOpen(dropId)).thenReturn(false);
            when(dropRedisStore.isHoldsEmpty(dropId)).thenReturn(true);

            holdExpireScheduler.expireHolds();

            verify(dropRedisStore).removeOpenDrop(dropId);
        }

        @Test
        @DisplayName("CLOSED 드롭이어도 holds가 남아있으면 Set에서 제거하지 않는다")
        void doesNotRemoveClosedDropFromSetWhenHoldsRemain() {
            UUID dropId = UUID.randomUUID();
            when(dropRedisStore.getOpenDropIds()).thenReturn(Set.of(dropId.toString()));
            when(dropRedisStore.getExpiredOrderIds(eq(dropId), anyLong())).thenReturn(Set.of());
            when(dropRedisStore.isOpen(dropId)).thenReturn(false);
            when(dropRedisStore.isHoldsEmpty(dropId)).thenReturn(false);

            holdExpireScheduler.expireHolds();

            verify(dropRedisStore, never()).removeOpenDrop(any());
        }

        @Test
        @DisplayName("CLOSED 드롭의 남은 hold를 만료시켜 비우면 hold.expired 발행 후 Set에서 제거한다")
        void expiresRemainingHoldsOfClosedDropThenRemovesFromSet() {
            UUID dropId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            when(dropRedisStore.getOpenDropIds()).thenReturn(Set.of(dropId.toString()));
            when(dropRedisStore.getExpiredOrderIds(eq(dropId), anyLong()))
                    .thenReturn(Set.of(orderId.toString()));
            when(dropRedisStore.expireHold(dropId, orderId)).thenReturn(1L);
            when(dropRedisStore.isOpen(dropId)).thenReturn(false);
            when(dropRedisStore.isHoldsEmpty(dropId)).thenReturn(true);

            holdExpireScheduler.expireHolds();

            verify(dropEventProducer).publishHoldExpired(argThat(e -> e.orderId().equals(orderId)));
            verify(dropRedisStore).removeOpenDrop(dropId);
        }
    }

    private Drop createOpenDrop() {
        LocalDateTime startAt = LocalDateTime.now().minusDays(1);
        Drop drop = Drop.create(UUID.randomUUID(), startAt, startAt.plusDays(2), 100, 600);
        ReflectionTestUtils.setField(drop, "dropId", UUID.randomUUID());
        drop.open();
        return drop;
    }

    private Drop createClosedDrop() {
        LocalDateTime startAt = LocalDateTime.now().minusDays(2);
        Drop drop = Drop.create(UUID.randomUUID(), startAt, startAt.plusHours(1), 100, 600);
        ReflectionTestUtils.setField(drop, "dropId", UUID.randomUUID());
        drop.open();
        drop.close();
        return drop;
    }
}
