package com.omc.drop.application.service;

import com.omc.drop.domain.exception.DuplicatePurchaseException;
import com.omc.drop.domain.exception.DropNotFoundException;
import com.omc.drop.domain.exception.DropNotOpenException;
import com.omc.drop.domain.exception.SoldOutException;
import com.omc.drop.infrastructure.metrics.DropMetrics;
import com.omc.drop.infrastructure.redis.DropRedisStore;
import com.omc.drop.presentation.dto.response.PurchaseResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.omc.common.util.UuidV7Generator;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final DropRedisStore dropRedisStore;
    private final DropMetrics dropMetrics;

    public PurchaseResponse purchase(UUID dropId, UUID userId) {
        // ① Redis OPEN 플래그 확인 — fail-fast
        if (!dropRedisStore.isOpen(dropId)) {
            throw new DropNotOpenException();
        }

        // ② holdTtlSec·productId를 Redis에서 조회 (워밍 시 캐싱된 값) — DB 무접촉 유지
        int holdTtlSec = dropRedisStore.getHoldTtlSec(dropId);
        UUID productId = dropRedisStore.getProductId(dropId);
        if (productId == null) {
            throw new DropNotFoundException();
        }

        // ③ orderId·eventId 선발급 — Lua 호출 전에 생성해서 Stream 메시지에 포함
        UUID orderId = UuidV7Generator.generate();
        String eventId = UuidV7Generator.generate().toString();

        // ④ Lua 원자 실행: 중복 체크 → 재고 체크 → 선점 → Stream XADD → 순번 발급
        long start = System.nanoTime();
        Long result = dropRedisStore.executePurchase(dropId, userId, orderId, holdTtlSec, productId, eventId);
        dropMetrics.recordLuaDuration(System.nanoTime() - start);

        if (result == null || result == -2L) {
            dropMetrics.incrementDuplicatePurchase(dropId);
            throw new DuplicatePurchaseException();
        }
        if (result == -1L) {
            dropMetrics.incrementSoldOut(dropId);
            throw new SoldOutException();
        }

        dropMetrics.incrementPurchaseSuccess(dropId);
        log.info("구매 선점 완료: dropId={}, userId={}, orderId={}, queueNumber={}", dropId, userId, orderId, result);
        return new PurchaseResponse(orderId, result);
    }
}
