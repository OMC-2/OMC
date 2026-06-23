package com.omc.drop.application.service;

import com.omc.drop.application.outbox.PurchaseOutboxWorker;
import com.omc.drop.domain.exception.DuplicatePurchaseException;
import com.omc.drop.domain.exception.DropNotFoundException;
import com.omc.drop.domain.exception.DropNotOpenException;
import com.omc.drop.domain.exception.SoldOutException;
import com.omc.drop.infrastructure.kafka.event.PurchaseConfirmedEvent;
import com.omc.drop.infrastructure.redis.PurchaseRedisRepository;
import com.omc.drop.presentation.dto.response.PurchaseResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final PurchaseRedisRepository purchaseRedisRepository;
    private final PurchaseOutboxWorker outboxWorker;

    public PurchaseResponse purchase(UUID dropId, UUID userId) {
        // ① Redis OPEN 플래그 확인 — fail-fast
        if (!purchaseRedisRepository.isOpen(dropId)) {
            throw new DropNotOpenException();
        }

        // ② holdTtlSec·productId를 Redis에서 조회 (워밍 시 캐싱된 값) — DB 무접촉 유지
        int holdTtlSec = purchaseRedisRepository.getHoldTtlSec(dropId);
        UUID productId = purchaseRedisRepository.getProductId(dropId);
        if (productId == null) {
            throw new DropNotFoundException();
        }

        // ③ orderId 선발급 — Lua ZADD member로 사용하므로 Lua 호출 전에 생성
        UUID orderId = UUID.randomUUID();

        // ④ Lua 원자 실행: 중복 체크 → 재고 체크 → 선점 → 순번 발급
        Long result = purchaseRedisRepository.executePurchase(dropId, userId, orderId, holdTtlSec);

        if (result == null || result == -2L) {
            throw new DuplicatePurchaseException();
        }
        if (result == -1L) {
            throw new SoldOutException();
        }

        // ⑤ 아웃박스 큐 적재 — 워커가 비동기로 purchase.confirmed 발행 (최대 3회 재시도)
        outboxWorker.enqueue(PurchaseConfirmedEvent.of(orderId, dropId, userId, productId, holdTtlSec));
        log.info("구매 선점 완료: dropId={}, userId={}, orderId={}, queueNumber={}", dropId, userId, orderId, result);

        return new PurchaseResponse(orderId, result);
    }
}
