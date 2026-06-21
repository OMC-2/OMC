package com.omc.drop.application.scheduler;

import com.omc.drop.application.event.producer.DropEventProducer;
import com.omc.drop.domain.entity.Drop;
import com.omc.drop.domain.enums.DropStatus;
import com.omc.drop.domain.repository.DropRepository;
import com.omc.drop.infrastructure.kafka.event.DropClosedEvent;
import com.omc.drop.infrastructure.kafka.event.DropOpenedEvent;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DropStatusScheduler 테스트")
class DropStatusSchedulerTest {

    @Mock
    private DropRepository dropRepository;

    @Mock
    private PurchaseRedisRepository purchaseRedisRepository;

    @Mock
    private DropEventProducer dropEventProducer;

    @InjectMocks
    private DropStatusScheduler dropStatusScheduler;

    @Nested
    @DisplayName("OPEN 전이")
    class OpenScheduledDrops {

        @Test
        @DisplayName("조건부 UPDATE 성공 시 Redis 워밍 후 drop.opened 발행")
        void opensDropAndPublishesEvent() {
            Drop drop = createScheduledDrop();
            when(dropRepository.findByStatusAndStartAtLessThanEqual(eq(DropStatus.SCHEDULED), any()))
                    .thenReturn(List.of(drop));
            when(dropRepository.updateStatusConditionally(drop.getDropId(), DropStatus.SCHEDULED, DropStatus.OPEN))
                    .thenReturn(1);

            dropStatusScheduler.openScheduledDrops();

            verify(purchaseRedisRepository).warmup(drop.getDropId(), drop.getTotalQty(), drop.getHoldTtlSec(), drop.getProductId());

            ArgumentCaptor<DropOpenedEvent> captor = ArgumentCaptor.forClass(DropOpenedEvent.class);
            verify(dropEventProducer).publishDropOpened(captor.capture());
            assertThat(captor.getValue().dropId()).isEqualTo(drop.getDropId());
            assertThat(captor.getValue().totalQty()).isEqualTo(drop.getTotalQty());
        }

        @Test
        @DisplayName("조건부 UPDATE 반환값 0이면 이벤트 발행 생략 (다른 인스턴스가 먼저 처리)")
        void skipsPublishWhenUpdateReturnsZero() {
            Drop drop = createScheduledDrop();
            when(dropRepository.findByStatusAndStartAtLessThanEqual(eq(DropStatus.SCHEDULED), any()))
                    .thenReturn(List.of(drop));
            when(dropRepository.updateStatusConditionally(drop.getDropId(), DropStatus.SCHEDULED, DropStatus.OPEN))
                    .thenReturn(0);

            dropStatusScheduler.openScheduledDrops();

            verify(dropEventProducer, never()).publishDropOpened(any());
        }

        @Test
        @DisplayName("Redis 워밍 실패 시 UPDATE·발행 생략하고 다음 드롭 계속 처리")
        void skipsOpenTransitionWhenRedisFails() {
            Drop failDrop = createScheduledDrop();
            Drop successDrop = createScheduledDrop();
            when(dropRepository.findByStatusAndStartAtLessThanEqual(eq(DropStatus.SCHEDULED), any()))
                    .thenReturn(List.of(failDrop, successDrop));
            doThrow(new RuntimeException("Redis 연결 실패"))
                    .when(purchaseRedisRepository).warmup(eq(failDrop.getDropId()), anyInt(), anyInt(), any());
            when(dropRepository.updateStatusConditionally(successDrop.getDropId(), DropStatus.SCHEDULED, DropStatus.OPEN))
                    .thenReturn(1);

            dropStatusScheduler.openScheduledDrops();

            verify(dropRepository, never()).updateStatusConditionally(eq(failDrop.getDropId()), any(), any());
            verify(dropEventProducer, never()).publishDropOpened(argThat(e -> e.dropId().equals(failDrop.getDropId())));
            verify(dropEventProducer).publishDropOpened(argThat(e -> e.dropId().equals(successDrop.getDropId())));
        }

        @Test
        @DisplayName("전이 대상이 없으면 아무 처리도 하지 않는다")
        void doesNothingWhenNoCandidates() {
            when(dropRepository.findByStatusAndStartAtLessThanEqual(eq(DropStatus.SCHEDULED), any()))
                    .thenReturn(List.of());

            dropStatusScheduler.openScheduledDrops();

            verify(dropRepository, never()).updateStatusConditionally(any(), any(), any());
            verify(dropEventProducer, never()).publishDropOpened(any());
        }
    }

    @Nested
    @DisplayName("CLOSE 전이")
    class CloseOpenDrops {

        @Test
        @DisplayName("조건부 UPDATE 성공 시 Redis 플래그 삭제 후 drop.closed 발행")
        void closesDropAndPublishesEvent() {
            Drop drop = createOpenDrop();
            when(dropRepository.findByStatusAndEndAtLessThanEqual(eq(DropStatus.OPEN), any()))
                    .thenReturn(List.of(drop));
            when(dropRepository.updateStatusConditionally(drop.getDropId(), DropStatus.OPEN, DropStatus.CLOSED))
                    .thenReturn(1);

            dropStatusScheduler.closeOpenDrops();

            verify(purchaseRedisRepository).deleteStatus(drop.getDropId());

            ArgumentCaptor<DropClosedEvent> captor = ArgumentCaptor.forClass(DropClosedEvent.class);
            verify(dropEventProducer).publishDropClosed(captor.capture());
            assertThat(captor.getValue().dropId()).isEqualTo(drop.getDropId());
        }

        @Test
        @DisplayName("조건부 UPDATE 반환값 0이면 Redis 삭제·이벤트 발행 생략")
        void skipsPublishWhenUpdateReturnsZero() {
            Drop drop = createOpenDrop();
            when(dropRepository.findByStatusAndEndAtLessThanEqual(eq(DropStatus.OPEN), any()))
                    .thenReturn(List.of(drop));
            when(dropRepository.updateStatusConditionally(drop.getDropId(), DropStatus.OPEN, DropStatus.CLOSED))
                    .thenReturn(0);

            dropStatusScheduler.closeOpenDrops();

            verify(purchaseRedisRepository, never()).deleteStatus(any());
            verify(dropEventProducer, never()).publishDropClosed(any());
        }

        @Test
        @DisplayName("전이 대상이 없으면 아무 처리도 하지 않는다")
        void doesNothingWhenNoCandidates() {
            when(dropRepository.findByStatusAndEndAtLessThanEqual(eq(DropStatus.OPEN), any()))
                    .thenReturn(List.of());

            dropStatusScheduler.closeOpenDrops();

            verify(dropRepository, never()).updateStatusConditionally(any(), any(), any());
            verify(dropEventProducer, never()).publishDropClosed(any());
        }
    }

    private Drop createScheduledDrop() {
        LocalDateTime startAt = LocalDateTime.now().minusMinutes(1);
        Drop drop = Drop.create(UUID.randomUUID(), startAt, startAt.plusDays(1), 100, 600);
        ReflectionTestUtils.setField(drop, "dropId", UUID.randomUUID());
        return drop;
    }

    private Drop createOpenDrop() {
        LocalDateTime startAt = LocalDateTime.now().minusDays(1);
        Drop drop = Drop.create(UUID.randomUUID(), startAt, startAt.plusHours(1), 100, 600);
        ReflectionTestUtils.setField(drop, "dropId", UUID.randomUUID());
        drop.open();
        return drop;
    }
}
