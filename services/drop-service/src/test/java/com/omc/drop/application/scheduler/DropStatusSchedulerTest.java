package com.omc.drop.application.scheduler;

import com.omc.common.response.ApiResponse;
import com.omc.drop.application.event.producer.DropEventProducer;
import com.omc.drop.application.event.producer.DropOpenedEvent;
import com.omc.drop.domain.entity.Drop;
import com.omc.drop.domain.enums.DropStatus;
import com.omc.drop.domain.repository.DropRepository;
import com.omc.drop.infrastructure.client.InventorySnapshotResponse;
import com.omc.drop.infrastructure.client.ProductServiceClient;
import com.omc.drop.infrastructure.metrics.DropMetrics;
import com.omc.drop.infrastructure.redis.DropRedisStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
    private DropRedisStore dropRedisStore;

    @Mock
    private DropEventProducer dropEventProducer;

    @Mock
    private ProductServiceClient productServiceClient;

    @Mock
    private DropMetrics dropMetrics;

    @InjectMocks
    private DropStatusScheduler dropStatusScheduler;

    @Nested
    @DisplayName("OPEN 전이")
    class OpenScheduledDrops {

        @Test
        @DisplayName("product-service 재고 조회 성공 시 availableQty로 Redis 워밍 후 drop.opened 발행")
        void opensDropWithAvailableQtyFromProductService() {
            Drop drop = createScheduledDrop();
            int availableQty = 80;
            when(dropRepository.findByStatusAndStartAtLessThanEqual(eq(DropStatus.SCHEDULED), any()))
                    .thenReturn(List.of(drop));
            when(productServiceClient.getInventorySnapshot(drop.getProductId()))
                    .thenReturn(ApiResponse.success(new InventorySnapshotResponse(availableQty)));
            when(dropRepository.updateStatusConditionally(drop.getDropId(), DropStatus.SCHEDULED, DropStatus.OPEN))
                    .thenReturn(1);

            dropStatusScheduler.openScheduledDrops();

            verify(dropRedisStore).warmup(drop.getDropId(), availableQty, drop.getHoldTtlSec(), drop.getProductId());
            verify(dropRedisStore).addOpenDrop(drop.getDropId());

            ArgumentCaptor<DropOpenedEvent> captor = ArgumentCaptor.forClass(DropOpenedEvent.class);
            verify(dropEventProducer).publishDropOpened(captor.capture());
            assertThat(captor.getValue().dropId()).isEqualTo(drop.getDropId());
        }

        @Test
        @DisplayName("product-service 장애 시 totalQty로 폴백하여 OPEN 전이 진행")
        void fallsBackToTotalQtyWhenProductServiceFails() {
            Drop drop = createScheduledDrop();
            when(dropRepository.findByStatusAndStartAtLessThanEqual(eq(DropStatus.SCHEDULED), any()))
                    .thenReturn(List.of(drop));
            when(productServiceClient.getInventorySnapshot(drop.getProductId()))
                    .thenThrow(new RuntimeException("product-service 연결 실패"));
            when(dropRepository.updateStatusConditionally(drop.getDropId(), DropStatus.SCHEDULED, DropStatus.OPEN))
                    .thenReturn(1);

            dropStatusScheduler.openScheduledDrops();

            verify(dropRedisStore).warmup(drop.getDropId(), drop.getTotalQty(), drop.getHoldTtlSec(), drop.getProductId());
            verify(dropRedisStore).addOpenDrop(drop.getDropId());
            verify(dropEventProducer).publishDropOpened(argThat(e -> e.dropId().equals(drop.getDropId())));
            verify(dropMetrics).incrementInventoryFallback(drop.getDropId());
        }

        @Test
        @DisplayName("조건부 UPDATE 반환값 0이면 이벤트 발행 생략 (다른 인스턴스가 먼저 처리)")
        void skipsPublishWhenUpdateReturnsZero() {
            Drop drop = createScheduledDrop();
            when(dropRepository.findByStatusAndStartAtLessThanEqual(eq(DropStatus.SCHEDULED), any()))
                    .thenReturn(List.of(drop));
            when(productServiceClient.getInventorySnapshot(drop.getProductId()))
                    .thenReturn(ApiResponse.success(new InventorySnapshotResponse(drop.getTotalQty())));
            when(dropRepository.updateStatusConditionally(drop.getDropId(), DropStatus.SCHEDULED, DropStatus.OPEN))
                    .thenReturn(0);

            dropStatusScheduler.openScheduledDrops();

            verify(dropRedisStore, never()).addOpenDrop(any());
            verify(dropEventProducer, never()).publishDropOpened(any());
        }

        @Test
        @DisplayName("Redis 워밍 실패 시 DB OPEN 전이·이벤트 발행은 진행하고 addOpenDrop만 생략")
        void continuesOpenTransitionWhenRedisFails() {
            Drop failDrop = createScheduledDrop();
            Drop successDrop = createScheduledDrop();
            when(dropRepository.findByStatusAndStartAtLessThanEqual(eq(DropStatus.SCHEDULED), any()))
                    .thenReturn(List.of(failDrop, successDrop));
            when(productServiceClient.getInventorySnapshot(any()))
                    .thenReturn(ApiResponse.success(new InventorySnapshotResponse(100)));
            when(dropRepository.updateStatusConditionally(failDrop.getDropId(), DropStatus.SCHEDULED, DropStatus.OPEN))
                    .thenReturn(1);
            when(dropRepository.updateStatusConditionally(successDrop.getDropId(), DropStatus.SCHEDULED, DropStatus.OPEN))
                    .thenReturn(1);
            doThrow(new RuntimeException("Redis 연결 실패"))
                    .when(dropRedisStore).warmup(eq(failDrop.getDropId()), anyInt(), anyInt(), any());

            dropStatusScheduler.openScheduledDrops();

            // failDrop: DB 업데이트 성공, warmup 실패 → addOpenDrop 미호출, 이벤트는 발행
            verify(dropRedisStore, never()).addOpenDrop(failDrop.getDropId());
            verify(dropEventProducer).publishDropOpened(argThat(e -> e.dropId().equals(failDrop.getDropId())));
            // successDrop: 정상 처리
            verify(dropRedisStore).addOpenDrop(successDrop.getDropId());
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
        @DisplayName("Redis status 선삭제 후 DB UPDATE 성공 시 drop.closed 발행")
        void closesDropAndPublishesEvent() {
            Drop drop = createOpenDrop();
            when(dropRepository.findByStatusAndEndAtLessThanEqual(eq(DropStatus.OPEN), any()))
                    .thenReturn(List.of(drop));
            when(dropRepository.updateStatusConditionally(drop.getDropId(), DropStatus.OPEN, DropStatus.CLOSED))
                    .thenReturn(1);

            dropStatusScheduler.closeOpenDrops();

            // 구매 차단 gap 방지: deleteStatus → updateStatusConditionally → publishDropClosed 순서 보장
            InOrder inOrder = inOrder(dropRedisStore, dropRepository, dropEventProducer);
            inOrder.verify(dropRedisStore).deleteStatus(drop.getDropId());
            inOrder.verify(dropRepository).updateStatusConditionally(drop.getDropId(), DropStatus.OPEN, DropStatus.CLOSED);
            inOrder.verify(dropEventProducer).publishDropClosed(any());
        }

        @Test
        @DisplayName("조건부 UPDATE 반환값 0이면 이벤트 발행만 생략 (Redis 선삭제는 항상 실행)")
        void skipsPublishWhenUpdateReturnsZero() {
            Drop drop = createOpenDrop();
            when(dropRepository.findByStatusAndEndAtLessThanEqual(eq(DropStatus.OPEN), any()))
                    .thenReturn(List.of(drop));
            when(dropRepository.updateStatusConditionally(drop.getDropId(), DropStatus.OPEN, DropStatus.CLOSED))
                    .thenReturn(0);

            dropStatusScheduler.closeOpenDrops();

            // Redis status는 구매 차단을 위해 항상 먼저 삭제 (다른 인스턴스가 먼저 DB 처리한 경우에도)
            verify(dropRedisStore).deleteStatus(drop.getDropId());
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
