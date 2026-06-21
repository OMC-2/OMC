package com.omc.drop.application.scheduler;

import com.omc.drop.application.event.producer.DropEventProducer;
import com.omc.drop.domain.entity.Drop;
import com.omc.drop.domain.enums.DropStatus;
import com.omc.drop.domain.repository.DropRepository;
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

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void openScheduledDrops() {
        List<Drop> candidates = dropRepository.findByStatusAndStartAtLessThanEqual(
                DropStatus.SCHEDULED, LocalDateTime.now());

        for (Drop drop : candidates) {
            try {
                // TODO: product-service GET /internal/v1/products/{productId}/stock 로 최신 재고 조회 후 설정 (현재는 드롭 생성 시 입력된 totalQty 사용)
                purchaseRedisRepository.warmup(drop.getDropId(), drop.getTotalQty(), drop.getHoldTtlSec(), drop.getProductId());
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
