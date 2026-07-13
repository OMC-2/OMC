package com.omc.drop.application.service;

import com.omc.common.util.UuidV7Generator;
import com.omc.drop.domain.exception.DuplicatePurchaseException;
import com.omc.drop.domain.exception.DropNotFoundException;
import com.omc.drop.domain.exception.DropNotOpenException;
import com.omc.drop.domain.exception.PurchaseReservationFailedException;
import com.omc.drop.domain.exception.SoldOutException;
import com.omc.drop.infrastructure.metrics.DropMetrics;
import com.omc.drop.infrastructure.redis.DropRedisStore;
import com.omc.drop.presentation.dto.response.PurchaseResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseService {

    private static final int ROLLBACK_MAX_ATTEMPTS = 3;
    private static final long[] ROLLBACK_BACKOFF_MS = {50L, 150L};

    private final DropRedisStore dropRedisStore;
    private final DropPurchaseReservationService reservationService;
    private final DropMetrics dropMetrics;

    public PurchaseResponse purchase(UUID dropId, UUID userId) {
        UUID orderId = UuidV7Generator.generate();

        // ① Lua 단일 호출: OPEN 확인 + 중복·재고 체크 + 원자적 선점 + 순번 발급
        long start = System.nanoTime();
        Long queueNumber = dropRedisStore.executePurchase(dropId, userId, orderId);
        dropMetrics.recordLuaDuration(System.nanoTime() - start);

        if (queueNumber == null || queueNumber == -3L) throw new DropNotOpenException();
        if (queueNumber == -4L)                        throw new DropNotFoundException();
        if (queueNumber == -2L) {
            dropMetrics.incrementDuplicatePurchase(dropId);
            throw new DuplicatePurchaseException();
        }
        if (queueNumber == -1L) {
            dropMetrics.incrementSoldOut(dropId);
            throw new SoldOutException();
        }

        // ② 선점 성공 건에 한해 DB 저장 (1,000명 중 재고 100개면 100건만 도달)
        try {
            long holdExpiresAtEpochSec = dropRedisStore.getHoldExpiresAtEpochSec(dropId, orderId);
            UUID productId = dropRedisStore.getProductId(dropId);
            reservationService.record(dropId, userId, orderId, productId, holdExpiresAtEpochSec, queueNumber);
        } catch (Exception e) {
            // Redis 선점은 성공했으나 hold/productId 조회 또는 DB 저장 실패 → Redis 선점 보상
            log.error("[Purchase] 선점 후속 처리 실패, Redis 보상 시작: dropId={}, orderId={}, userId={}",
                    dropId, orderId, userId, e);
            rollbackPurchaseClaimWithRetry(dropId, orderId, userId);
            throw new PurchaseReservationFailedException();
        }

        // DB reservation 저장 성공 후에 메트릭 증가 — 보상 처리된 건은 성공으로 카운트하지 않음
        dropMetrics.incrementPurchaseSuccess(dropId);
        log.info("구매 선점 완료: dropId={}, userId={}, orderId={}, queueNumber={}", dropId, userId, orderId, queueNumber);
        return new PurchaseResponse(orderId, queueNumber);
    }

    private void rollbackPurchaseClaimWithRetry(UUID dropId, UUID orderId, UUID userId) {
        for (int attempt = 1; attempt <= ROLLBACK_MAX_ATTEMPTS; attempt++) {
            try {
                long removed = dropRedisStore.rollbackPurchaseClaim(dropId, orderId, userId);
                if (removed == 0) {
                    log.warn("[Purchase] hold 없음 — purchased marker만 정리: dropId={}, orderId={}", dropId, orderId);
                } else {
                    log.info("[Purchase] Redis 보상 성공 (attempt={}): dropId={}, orderId={}", attempt, dropId, orderId);
                }
                return;
            } catch (Exception re) {
                log.warn("[Purchase] Redis 보상 실패 (attempt={}): dropId={}, orderId={}", attempt, dropId, orderId, re);
                if (attempt < ROLLBACK_MAX_ATTEMPTS) {
                    try { Thread.sleep(ROLLBACK_BACKOFF_MS[attempt - 1]); }
                    catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("[Purchase] Redis 보상 재시도 대기 중 인터럽트, 보상 중단: dropId={}, orderId={}",
                                dropId, orderId, ie);
                        break;
                    }
                }
            }
        }
        log.error("[Purchase][CRITICAL] Redis 보상 최종 실패 — 수동 확인 필요: dropId={}, orderId={}, userId={}",
                dropId, orderId, userId);
        dropMetrics.incrementRedisCompensationFailed(dropId);
    }
}
