package com.omc.drop.application.scheduler;

import com.omc.drop.domain.entity.Drop;
import com.omc.drop.domain.enums.DropStatus;
import com.omc.drop.domain.repository.DropRepository;
import com.omc.drop.infrastructure.redis.PurchaseRedisRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DropRedisCleanupScheduler 테스트")
class DropRedisCleanupSchedulerTest {

    @Mock
    private DropRepository dropRepository;

    @Mock
    private PurchaseRedisRepository purchaseRedisRepository;

    @InjectMocks
    private DropRedisCleanupScheduler dropRedisCleanupScheduler;

    @Nested
    @DisplayName("Redis 키 정리")
    class CleanupClosedDrops {

        @Test
        @DisplayName("시간 버퍼 경과 + holds 비었으면 키를 삭제하고 로그를 찍는다")
        void deletesKeysWhenReadyForCleanup() {
            Drop drop = createClosedDrop(LocalDateTime.now().minusHours(2));
            when(dropRepository.findByStatus(DropStatus.CLOSED)).thenReturn(List.of(drop));
            when(purchaseRedisRepository.isHoldsEmpty(drop.getDropId())).thenReturn(true);
            when(purchaseRedisRepository.deleteDropKeys(drop.getDropId())).thenReturn(6L);

            dropRedisCleanupScheduler.cleanupClosedDrops();

            verify(purchaseRedisRepository).deleteDropKeys(drop.getDropId());
        }

        @Test
        @DisplayName("시간 버퍼가 아직 안 지났으면 삭제하지 않는다")
        void skipsWhenBufferNotElapsed() {
            Drop drop = createClosedDrop(LocalDateTime.now().minusMinutes(30));
            when(dropRepository.findByStatus(DropStatus.CLOSED)).thenReturn(List.of(drop));

            dropRedisCleanupScheduler.cleanupClosedDrops();

            verify(purchaseRedisRepository, never()).deleteDropKeys(any());
        }

        @Test
        @DisplayName("holds가 남아있으면 삭제하지 않는다")
        void skipsWhenHoldsNotEmpty() {
            Drop drop = createClosedDrop(LocalDateTime.now().minusHours(2));
            when(dropRepository.findByStatus(DropStatus.CLOSED)).thenReturn(List.of(drop));
            when(purchaseRedisRepository.isHoldsEmpty(drop.getDropId())).thenReturn(false);

            dropRedisCleanupScheduler.cleanupClosedDrops();

            verify(purchaseRedisRepository, never()).deleteDropKeys(any());
        }

        @Test
        @DisplayName("이미 키가 삭제된 경우 deleteDropKeys 반환값이 0이면 로그를 찍지 않는다")
        void doesNotLogWhenAlreadyCleaned() {
            Drop drop = createClosedDrop(LocalDateTime.now().minusHours(2));
            when(dropRepository.findByStatus(DropStatus.CLOSED)).thenReturn(List.of(drop));
            when(purchaseRedisRepository.isHoldsEmpty(drop.getDropId())).thenReturn(true);
            when(purchaseRedisRepository.deleteDropKeys(drop.getDropId())).thenReturn(0L);

            dropRedisCleanupScheduler.cleanupClosedDrops();

            verify(purchaseRedisRepository).deleteDropKeys(drop.getDropId());
        }

        @Test
        @DisplayName("CLOSED 드롭이 없으면 아무 처리도 하지 않는다")
        void doesNothingWhenNoClosedDrops() {
            when(dropRepository.findByStatus(DropStatus.CLOSED)).thenReturn(List.of());

            dropRedisCleanupScheduler.cleanupClosedDrops();

            verify(purchaseRedisRepository, never()).isHoldsEmpty(any());
            verify(purchaseRedisRepository, never()).deleteDropKeys(any());
        }
    }

    private Drop createClosedDrop(LocalDateTime endAt) {
        LocalDateTime startAt = endAt.minusHours(1);
        Drop drop = Drop.create(UUID.randomUUID(), startAt, endAt, 100, 600);
        ReflectionTestUtils.setField(drop, "dropId", UUID.randomUUID());
        drop.open();
        drop.close();
        return drop;
    }
}
