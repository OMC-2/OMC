package com.omc.raffle.application.service;

import com.omc.common.exception.BusinessException;
import com.omc.raffle.application.dto.request.RaffleApplyRequest;
import com.omc.raffle.application.dto.response.RaffleApplyResponse;
import com.omc.raffle.domain.entity.Raffle;
import com.omc.raffle.domain.entity.RaffleEntry;
import com.omc.raffle.domain.enums.RaffleStatus;
import com.omc.raffle.domain.exception.RaffleErrorCode;
import com.omc.raffle.domain.repository.RaffleEntryRepository;
import com.omc.raffle.domain.repository.RaffleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import com.omc.raffle.infrastructure.client.PaymentClient;
import com.omc.raffle.infrastructure.redis.RaffleEntryRedisRepository;

/**
 * 래플 응모 및 관련된 전반적인 비즈니스 로직을 처리하는 Application Service.
 * Redis 기반 중복 검증 로직, Feign 기반 결제 가승인 통신, 응모 내역 저장 로직을 관장합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RaffleAppService {

    private final RaffleRepository raffleRepository;
    private final RaffleEntryRepository raffleEntryRepository;
    private final RaffleEntryRedisRepository redisRepository;
    private final PaymentClient paymentClient;

    /**
     * 래플 응모 로직
     */
    @Transactional
    public RaffleApplyResponse apply(UUID raffleId, RaffleApplyRequest request) {
        // 1. 래플 조회
        Raffle raffle = raffleRepository.findById(raffleId)
                .orElseThrow(() -> new BusinessException(RaffleErrorCode.RAFFLE_001));

        // 2. 래플 상태 검증
        if (raffle.getStatus() != RaffleStatus.OPEN) {
            throw new BusinessException(RaffleErrorCode.RAFFLE_003); // 진행 중인 래플이 아님
        }

        // 3. 중복 응모 검증 (Redis SADD 활용)
        boolean isAdded = redisRepository.addEntry(raffleId, request.userId());
        if (!isAdded) {
            throw new BusinessException(RaffleErrorCode.RAFFLE_002); // 이미 응모함
        }

        // 4. 결제 수단 유효성 검증 (가승인)
        try {
            // 결제 서버에 100원 가승인 요청 (이후 결제 서버 내에서 자동 승인 취소됨)
            paymentClient.preAuthCard(request.billingKeyId(), new java.math.BigDecimal("100"));
        } catch (Exception e) {
            // SAGA 보상 트랜잭션: 결제 수단 유효성 검증에 실패하면 이미 SADD된 Redis 값을 제거해야 할 수도 있음
            // 또는 비즈니스 로직에 따라 Redis Expire 시간을 짧게 주어 자연스레 만료되게 할 수도 있음.
            log.error("[RaffleAppService] 결제 수단 가승인 실패. userId={}, billingKeyId={}", request.userId(), request.billingKeyId(), e);
            throw new BusinessException(RaffleErrorCode.RAFFLE_004, "결제 수단(카드) 검증에 실패했습니다.");
        }

        // 5. 응모 내역 저장
        RaffleEntry entry = RaffleEntry.create(
                raffleId, 
                request.userId(), 
                request.billingKeyId(),
                request.couponId(),
                request.originalAmount(),
                request.discountAmount(),
                request.finalAmount()
        );
        RaffleEntry savedEntry = raffleEntryRepository.save(entry);

        return new RaffleApplyResponse(
                savedEntry.getId(),
                savedEntry.getRaffleId(),
                savedEntry.getUserId(),
                savedEntry.getBillingKeyId(),
                savedEntry.getCouponId(),
                savedEntry.getOriginalAmount(),
                savedEntry.getDiscountAmount(),
                savedEntry.getFinalAmount(),
                savedEntry.getEnteredAt()
        );
    }
}
