package com.omc.raffle.application.service;

import com.omc.common.exception.BusinessException;
import com.omc.raffle.application.dto.response.RaffleResultResponse;
import com.omc.raffle.domain.entity.RaffleResult;
import com.omc.raffle.domain.exception.RaffleErrorCode;
import com.omc.raffle.domain.repository.RaffleResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RaffleResultService {

    private final RaffleResultRepository raffleResultRepository;

    public RaffleResultResponse getResult(UUID raffleId, UUID userId) {
        RaffleResult result = raffleResultRepository.findByRaffleIdAndUserId(raffleId, userId)
                .orElseThrow(() -> new BusinessException(RaffleErrorCode.RAFFLE_001, "해당 래플에 응모한 내역이 없거나 아직 추첨되지 않았습니다."));

        return new RaffleResultResponse(
                result.getId(),
                result.getRaffleId(),
                result.getUserId(),
                result.getResult(),
                result.getDecidedAt()
        );
    }
}
