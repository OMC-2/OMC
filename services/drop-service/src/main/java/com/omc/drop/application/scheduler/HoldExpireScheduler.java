package com.omc.drop.application.scheduler;

import com.omc.drop.application.event.producer.DropEventProducer;
import com.omc.drop.domain.entity.Drop;
import com.omc.drop.domain.enums.DropStatus;
import com.omc.drop.domain.repository.DropRepository;
import com.omc.drop.application.event.producer.HoldExpiredEvent;
import com.omc.drop.infrastructure.metrics.DropMetrics;
import com.omc.drop.infrastructure.redis.DropRedisStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class HoldExpireScheduler {

    private final DropRepository dropRepository;
    private final DropRedisStore dropRedisStore;
    private final DropEventProducer dropEventProducer;
    private final DropMetrics dropMetrics;

    @Scheduled(fixedDelay = 10000)
    public void expireHolds() {
        Set<String> openDropIds = dropRedisStore.getOpenDropIds();

        if (openDropIds == null || openDropIds.isEmpty()) {
            openDropIds = recoverFromDb();
        }

        long nowEpoch = Instant.now().getEpochSecond();
        for (String dropIdStr : openDropIds) {
            UUID dropId = UUID.fromString(dropIdStr);
            processExpiredHolds(dropId, nowEpoch);
            if (!dropRedisStore.isOpen(dropId) && dropRedisStore.isHoldsEmpty(dropId)) {
                dropRedisStore.removeOpenDrop(dropId);
                log.info("CLOSED 드롭 open_drops 제거 완료. dropId={}", dropId);
            }
        }
    }

    private static final int RECOVERY_LOOKBACK_DAYS = 2;

    private Set<String> recoverFromDb() {
        log.warn("open_drops Set이 비었습니다. DB에서 복구합니다.");
        Set<String> recovered = new HashSet<>();

        List<Drop> openDrops = dropRepository.findByStatus(DropStatus.OPEN);
        openDrops.forEach(drop -> {
            dropRedisStore.addOpenDrop(drop.getDropId());
            recovered.add(drop.getDropId().toString());
        });

        // CLOSED이지만 holds가 남은 드롭도 복구 (재시작 시 누락 방지)
        LocalDateTime lookback = LocalDateTime.now().minusDays(RECOVERY_LOOKBACK_DAYS);
        List<Drop> recentClosed = dropRepository.findByStatusAndEndAtGreaterThanEqual(DropStatus.CLOSED, lookback);
        recentClosed.stream()
                .filter(drop -> !dropRedisStore.isHoldsEmpty(drop.getDropId()))
                .forEach(drop -> {
                    dropRedisStore.addOpenDrop(drop.getDropId());
                    recovered.add(drop.getDropId().toString());
                });

        return recovered;
    }

    private void processExpiredHolds(UUID dropId, long nowEpoch) {
        Set<String> expiredOrderIds = dropRedisStore.getExpiredOrderIds(dropId, nowEpoch);
        if (expiredOrderIds == null || expiredOrderIds.isEmpty()) {
            return;
        }

        for (String orderIdStr : expiredOrderIds) {
            try {
                UUID orderId = UUID.fromString(orderIdStr);
                long removed = dropRedisStore.expireHold(dropId, orderId);
                if (removed == 0) {
                    continue;
                }
                dropMetrics.incrementHoldExpired(dropId);
                dropEventProducer.publishHoldExpired(HoldExpiredEvent.of(orderId, dropId));
                log.info("hold 만료 처리 완료. dropId={}, orderId={}", dropId, orderId);
            } catch (Exception e) {
                log.error("hold 만료 처리 실패. dropId={}, orderId={}", dropId, orderIdStr, e);
            }
        }
    }
}
