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

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RaffleAppService {

    private final RaffleRepository raffleRepository;
    private final RaffleEntryRepository raffleEntryRepository;

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

        // 3. 중복 응모 검증
        // TODO (STEP 7): DB 조회 전에 Redis SADD 연산을 통한 동시성 제어 로직 선행 필수
        if (raffleEntryRepository.existsByRaffleIdAndUserId(raffleId, request.userId())) {
            throw new BusinessException(RaffleErrorCode.RAFFLE_002); // 이미 응모함
        }

        // 4. 결제 수단 유효성 검증 (가승인)
        // TODO (STEP 8): FeignClient를 통해 payment-service API 호출하여 빌링키 유효성 검사

        // 5. 응모 내역 저장
        RaffleEntry entry = RaffleEntry.create(raffleId, request.userId(), request.billingKeyId());
        RaffleEntry savedEntry = raffleEntryRepository.save(entry);

        return RaffleApplyResponse.from(savedEntry);
    }
}
