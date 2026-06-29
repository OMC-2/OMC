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
import com.omc.raffle.domain.exception.RaffleErrorCode;
import com.omc.raffle.domain.repository.RaffleEntryRepository;
import com.omc.raffle.domain.repository.RaffleRepository;
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
    private final RaffleEntryRedisRepository redisRepository;
    private final PaymentFeignClient paymentFeignClient;

    /**
     * 래플 응모 로직
     */
    @Transactional
    public RaffleApplyResponse apply(UUID raffleId, RaffleApplyRequest request) {
        // 1. 래플 조회
        Raffle raffle = raffleRepository.findById(raffleId)
                .orElseThrow(() -> new RaffleNotFoundException(RaffleErrorCode.RAFFLE_001));

        // 2. 래플 상태 검증
        if (raffle.getStatus() != RaffleStatus.OPEN) {
            throw new RaffleNotOpenException(RaffleErrorCode.RAFFLE_003); // 진행 중인 래플이 아님
        }

        // 3. 중복 응모 검증 (Redis SADD 활용)
        boolean isAdded = redisRepository.addEntry(raffleId, request.userId());
        if (!isAdded) {
            throw new DuplicateEntryException(RaffleErrorCode.RAFFLE_002); // 이미 응모함
        }

        // 4. 결제 수단 유효성 검증 (가승인)
        try {
            // 결제 서버에 100원 가승인 요청 (이후 결제 서버 내에서 자동 승인 취소됨)
            paymentFeignClient.preAuthCard(new PreAuthRequest(request.billingKeyId(), new java.math.BigDecimal("100")));
        } catch (Exception e) {
            // SAGA 보상 트랜잭션: 결제 수단 가승인 실패 시 이미 SADD된 Redis 값을 제거
            log.error("[RaffleAppService] 결제 수단 가승인 실패. userId={}, billingKeyId={}", request.userId(), request.billingKeyId(), e);
            redisRepository.removeEntry(raffleId, request.userId());
            throw new PaymentPreAuthFailedException(RaffleErrorCode.RAFFLE_004, "결제 수단(카드) 검증에 실패했습니다.");
        }

        // 5. 응모 내역 저장
        RaffleEntry savedEntry;
        try {
            RaffleEntry entry = RaffleEntry.create(
                    raffleId, 
                    request.userId(), 
                    request.billingKeyId(),
                    request.couponId(),
                    request.originalAmount(),
                    request.discountAmount(),
                    request.finalAmount()
            );
            savedEntry = raffleEntryRepository.save(entry);
        } catch (Exception e) {
            log.error("[RaffleAppService] DB 저장 실패로 인한 Redis 보상 처리. userId={}, raffleId={}", request.userId(), raffleId, e);
            redisRepository.removeEntry(raffleId, request.userId());
            throw new com.omc.raffle.domain.exception.RaffleEntryFailedException(RaffleErrorCode.RAFFLE_010, "DB 저장 중 오류가 발생했습니다.");
        }

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

