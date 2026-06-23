package com.omc.drop.application.scheduler;

import com.omc.drop.application.event.producer.DropEventProducer;
import com.omc.drop.domain.entity.Drop;
import com.omc.drop.domain.enums.DropStatus;
import com.omc.drop.domain.repository.DropRepository;
import com.omc.drop.infrastructure.client.ProductServiceClient;
import com.omc.drop.infrastructure.client.InventorySnapshotResponse;
import com.omc.drop.infrastructure.kafka.event.DropClosedEvent;
import com.omc.drop.infrastructure.kafka.event.DropOpenedEvent;
import com.omc.drop.infrastructure.redis.PurchaseRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DropStatusScheduler {

    private final DropRepository dropRepository;
    private final PurchaseRedisRepository purchaseRedisRepository;
    private final DropEventProducer dropEventProducer;
    private final ProductServiceClient productServiceClient;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void openScheduledDrops() {
        List<Drop> candidates = dropRepository.findByStatusAndStartAtLessThanEqual(
                DropStatus.SCHEDULED, LocalDateTime.now());

        for (Drop drop : candidates) {
            try {
                int availableQty = getAvailableQty(drop);
                purchaseRedisRepository.warmup(drop.getDropId(), availableQty, drop.getHoldTtlSec(), drop.getProductId());
            } catch (Exception e) {
                // Redis 워밍 실패 시 전이 생략 — 다음 폴링 주기에 재시도
                log.error("Redis 워밍 실패로 OPEN 전이 생략: dropId={}", drop.getDropId(), e);
                continue;
            }

            int updated = dropRepository.updateStatusConditionally(
                    drop.getDropId(), DropStatus.SCHEDULED, DropStatus.OPEN);
            if (updated == 1) {
                dropEventProducer.publishDropOpened(DropOpenedEvent.from(drop));
                log.info("드롭 OPEN 전이 완료: dropId={}", drop.getDropId());
            }
        }
    }

    private int getAvailableQty(Drop drop) {
        try {
            InventorySnapshotResponse snapshot = productServiceClient.getInventorySnapshot(drop.getProductId()).getData();
            return snapshot.availableQuantity();
        } catch (Exception e) {
            log.warn("product-service 재고 조회 실패, totalQty 폴백. dropId={}", drop.getDropId(), e);
            return drop.getTotalQty();
        }
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void closeOpenDrops() {
        List<Drop> candidates = dropRepository.findByStatusAndEndAtLessThanEqual(
                DropStatus.OPEN, LocalDateTime.now());

        for (Drop drop : candidates) {
            int updated = dropRepository.updateStatusConditionally(
                    drop.getDropId(), DropStatus.OPEN, DropStatus.CLOSED);
            if (updated == 1) {
                // status만 즉시 삭제 (신규 진입 차단)
                // hold_ttl, product_id, stock, purchased, holds, queue는 정산 후 배치 정리
                purchaseRedisRepository.deleteStatus(drop.getDropId());
                dropEventProducer.publishDropClosed(DropClosedEvent.from(drop));
                log.info("드롭 CLOSE 전이 완료: dropId={}", drop.getDropId());
            }
        }
    }
}
