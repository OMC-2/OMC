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
        // ① orderId·eventId 선발급 — Lua 호출 전에 생성해서 Stream 메시지에 포함
        UUID orderId = UuidV7Generator.generate();
        String eventId = UuidV7Generator.generate().toString();

        // ② Lua 단일 호출: OPEN 확인 + holdTtlSec/productId 조회 + 중복·재고 체크 + 선점 + Stream XADD + 순번 발급
        //    (기존 3번의 개별 Redis GET 제거 → 4 round-trips → 1 round-trip)
        long start = System.nanoTime();
        Long result = dropRedisStore.executePurchase(dropId, userId, orderId, eventId);
        dropMetrics.recordLuaDuration(System.nanoTime() - start);

        if (result == null || result == -3L) {
            throw new DropNotOpenException();
        }
        if (result == -4L) {
            throw new DropNotFoundException();
        }
        if (result == -2L) {
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
