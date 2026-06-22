package com.omc.drop.application.scheduler;

import com.omc.drop.domain.entity.Drop;
import com.omc.drop.domain.enums.DropStatus;
import com.omc.drop.domain.repository.DropRepository;
import com.omc.drop.infrastructure.redis.PurchaseRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DropRedisCleanupScheduler {

    private static final int CLEANUP_BUFFER_HOURS = 1;

    private final DropRepository dropRepository;
    private final PurchaseRedisRepository purchaseRedisRepository;

    // TODO: CLOSED 드롭 누적 시 endAt 범위 제한 또는 SETTLED 상태 추가 고려
    @Scheduled(fixedDelay = 60000)
    public void cleanupClosedDrops() {
        List<Drop> closedDrops = dropRepository.findByStatus(DropStatus.CLOSED);
        LocalDateTime now = LocalDateTime.now();

        for (Drop drop : closedDrops) {
            if (!isReadyForCleanup(drop, now)) {
                continue;
            }
            long deleted = purchaseRedisRepository.deleteDropKeys(drop.getDropId());
            if (deleted > 0) {
                log.info("Redis 키 정리 완료. dropId={}, 삭제 키={}", drop.getDropId(), deleted);
            }
        }
    }

    private boolean isReadyForCleanup(Drop drop, LocalDateTime now) {
        return drop.getEndAt().plusHours(CLEANUP_BUFFER_HOURS).isBefore(now)
                && purchaseRedisRepository.isHoldsEmpty(drop.getDropId());
    }
}
