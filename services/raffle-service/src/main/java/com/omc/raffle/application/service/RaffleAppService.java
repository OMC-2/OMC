package com.omc.raffle.application.service;
import com.omc.raffle.domain.exception.RaffleNotFoundException;
import com.omc.raffle.domain.exception.PaymentPreAuthFailedException;
import com.omc.raffle.domain.exception.RaffleNotOpenException;
import com.omc.raffle.domain.exception.DuplicateEntryException;

import com.omc.common.exception.BusinessException;
import com.omc.raffle.presentation.dto.request.RaffleApplyRequest;
import com.omc.raffle.presentation.dto.response.RaffleApplyResponse;
import com.omc.raffle.domain.entity.Raffle;
import com.omc.raffle.domain.entity.RaffleEntry;
import com.omc.raffle.domain.enums.RaffleStatus;
import com.omc.raffle.domain.enums.RaffleErrorCode;
import com.omc.raffle.domain.repository.RaffleEntryRepository;
import com.omc.raffle.domain.repository.RaffleRepository;
import com.omc.raffle.domain.repository.RafflePenaltyRepository;
import com.omc.raffle.domain.exception.PenaltyActiveException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.omc.common.response.PageResponse;
import com.omc.raffle.presentation.dto.response.RaffleResponse;
import com.omc.raffle.presentation.dto.response.RaffleEntryResponse;

import com.omc.raffle.infrastructure.client.PaymentFeignClient;
import com.omc.raffle.infrastructure.client.dto.PreAuthRequest;
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
    private final RafflePenaltyRepository rafflePenaltyRepository;
    private final RaffleEntryRedisRepository redisRepository;
    private final PaymentFeignClient paymentFeignClient;
    private final org.springframework.cache.CacheManager cacheManager;
    private static final java.util.concurrent.ConcurrentHashMap<UUID, Boolean> localCache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 래플 응모 로직
     */
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public RaffleApplyResponse apply(UUID raffleId, RaffleApplyRequest request) {
        // 1. 래플 조회 (수동 인메모리 캐싱으로 DB 커넥션 풀 고갈 원천 차단)
        org.springframework.cache.Cache cache = cacheManager.getCache("raffle");
        Raffle raffle;
        if (cache != null && cache.get(raffleId) != null) {
            raffle = (Raffle) cache.get(raffleId).get();
        } else {
            raffle = raffleRepository.findById(raffleId)
                    .orElseThrow(() -> new RaffleNotFoundException(RaffleErrorCode.RAFFLE_001));
            if (cache != null) cache.put(raffleId, raffle);
        }

        // 2. 래플 상태 검증
        if (raffle.getStatus() != RaffleStatus.OPEN) {
            throw new RaffleNotOpenException(RaffleErrorCode.RAFFLE_003); // 진행 중인 래플이 아님
        }

        // 3. 패널티 여부 검증
        boolean isPenaltyActive = rafflePenaltyRepository.existsByUserIdAndPenaltyEndDateAfter(request.userId(), LocalDateTime.now());
        if (isPenaltyActive) {
            throw new PenaltyActiveException(RaffleErrorCode.RAFFLE_007, "현재 패널티 상태이므로 응모할 수 없습니다.");
        }

        // 4. 중복 응모 검증 (Redis SADD 활용)
        boolean isAdded = redisRepository.addEntry(raffleId, request.userId());
        if (!isAdded) {
            throw new DuplicateEntryException(RaffleErrorCode.RAFFLE_002); // 이미 응모함
        }

        // 응모 내역 임시 생성
        RaffleEntry entry = RaffleEntry.create(
                raffleId, request.userId(), request.billingKeyId(), request.couponId(),
                request.originalAmount(), request.discountAmount(), request.finalAmount()
        );

        // 5. 비동기 처리: 결제 서버 통신(외부 API) 및 DB INSERT를 톰캣 스레드에서 분리
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                // 결제 서버에 100원 가승인 요청
                paymentFeignClient.preAuthCard(new PreAuthRequest(request.billingKeyId(), java.math.BigDecimal.valueOf(100)));
                // 응모 내역 DB 저장
                raffleEntryRepository.save(entry);
            } catch (Exception e) {
                log.error("[RaffleAppService] 비동기 처리 중 오류 발생 (결제 또는 DB). userId={}, raffleId={}", request.userId(), raffleId, e);
                // 보상 처리: Redis 롤백
                redisRepository.removeEntry(raffleId, request.userId());
            }
        });

        return new RaffleApplyResponse(
                entry.getId(), entry.getRaffleId(), entry.getUserId(), entry.getBillingKeyId(),
                entry.getCouponId(), entry.getOriginalAmount(), entry.getDiscountAmount(),
                entry.getFinalAmount(), entry.getEnteredAt()
        );
    }

    /**
     * 래플 목록 조회
     */
    public PageResponse<RaffleResponse> getRaffles(Pageable pageable) {
        Page<RaffleResponse> page = raffleRepository.findAll(pageable)
                .map(RaffleResponse::from);
        return new PageResponse<>(page);
    }

    /**
     * 래플 상세 단건 조회
     */
    public RaffleResponse getRaffle(UUID raffleId) {
        Raffle raffle = raffleRepository.findById(raffleId)
                .orElseThrow(() -> new RaffleNotFoundException(RaffleErrorCode.RAFFLE_001));
        return RaffleResponse.from(raffle);
    }

    /**
     * 내 응모 내역 조회
     */
    public PageResponse<RaffleEntryResponse> getMyEntries(UUID userId, Pageable pageable) {
        Page<RaffleEntryResponse> page = raffleEntryRepository.findByUserId(userId, pageable)
                .map(RaffleEntryResponse::from);
        return new PageResponse<>(page);
    }

    /**
     * 특정 래플의 실시간 응모자 수를 반환합니다.
     */
    public long getParticipantsCount(UUID raffleId) {
        // 래플 존재 여부 검증 (옵션 - 부하 방지를 위해 생략 가능하나 무결성을 위해 추가)
        if (!raffleRepository.existsById(raffleId)) {
            throw new RaffleNotFoundException(RaffleErrorCode.RAFFLE_001);
        }
        return redisRepository.getEntryCount(raffleId);
    }
}

