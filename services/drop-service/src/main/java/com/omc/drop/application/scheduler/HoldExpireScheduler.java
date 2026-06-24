package com.omc.drop.application.scheduler;

import com.omc.drop.application.event.producer.DropEventProducer;
import com.omc.drop.domain.entity.Drop;
import com.omc.drop.domain.enums.DropStatus;
import com.omc.drop.domain.repository.DropRepository;
import com.omc.drop.application.event.producer.HoldExpiredEvent;
import com.omc.drop.infrastructure.redis.PurchaseRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class HoldExpireScheduler {

    private final DropRepository dropRepository;
    private final PurchaseRedisRepository purchaseRedisRepository;
    private final DropEventProducer dropEventProducer;

    @Scheduled(fixedDelay = 10000)
    public void expireHolds() {
        List<Drop> openDrops = dropRepository.findByStatus(DropStatus.OPEN);
        long nowEpoch = Instant.now().getEpochSecond();

        for (Drop drop : openDrops) {
            processExpiredHolds(drop.getDropId(), nowEpoch);
        }
    }

    private void processExpiredHolds(UUID dropId, long nowEpoch) {
        Set<String> expiredOrderIds = purchaseRedisRepository.getExpiredOrderIds(dropId, nowEpoch);
        if (expiredOrderIds == null || expiredOrderIds.isEmpty()) {
            return;
        }

        for (String orderIdStr : expiredOrderIds) {
            try {
                UUID orderId = UUID.fromString(orderIdStr);
                long removed = purchaseRedisRepository.expireHold(dropId, orderId);
                if (removed == 0) {
                    continue;
                }
                dropEventProducer.publishHoldExpired(HoldExpiredEvent.of(orderId, dropId));
                log.info("hold 만료 처리 완료. dropId={}, orderId={}", dropId, orderId);
            } catch (Exception e) {
                log.error("hold 만료 처리 실패. dropId={}, orderId={}", dropId, orderIdStr, e);
            }
        }
    }
}
