package com.omc.raffle.application.service;
import com.omc.raffle.domain.exception.RaffleNotFoundException;

import com.omc.common.exception.BusinessException;
import com.omc.raffle.presentation.dto.response.RaffleResultResponse;
import com.omc.raffle.domain.entity.RaffleResult;
import com.omc.raffle.domain.repository.RaffleResultRepository;
import com.omc.raffle.domain.enums.RaffleErrorCode;
import com.omc.raffle.domain.repository.RaffleRepository;
import com.omc.raffle.domain.repository.RaffleEntryRepository;
import com.omc.raffle.domain.entity.Raffle;
import com.omc.raffle.domain.enums.RaffleResultStatus;
import com.omc.raffle.presentation.dto.response.PublicRaffleResultResponse;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RaffleResultService {
    private final RaffleResultRepository raffleResultRepository;
    private final RaffleRepository raffleRepository;
    private final RaffleEntryRepository raffleEntryRepository;
    public RaffleResultResponse getResult(UUID raffleId, UUID userId) {
        RaffleResult result = raffleResultRepository.findByRaffleIdAndUserId(raffleId, userId)
                .orElseThrow(() -> new RaffleNotFoundException(RaffleErrorCode.RAFFLE_001, "해당 래플에 응모한 내역이 없거나 아직 추첨되지 않았습니다."));

        return new RaffleResultResponse(
                result.getId(),
                result.getRaffleId(),
                result.getUserId(),
                result.getResult(),
                result.getDecidedAt()
        );
    }

    public List<PublicRaffleResultResponse> getPublicResults(UUID raffleId) {
        Raffle raffle = raffleRepository.findById(raffleId)
                .orElseThrow(() -> new com.omc.raffle.domain.exception.RaffleNotFoundException(com.omc.raffle.domain.enums.RaffleErrorCode.RAFFLE_001));

        if (raffle.getDrawSeed() == null) {
            throw new com.omc.raffle.domain.exception.DrawSeedNotGeneratedException(com.omc.raffle.domain.enums.RaffleErrorCode.RAFFLE_005);
        }


        List<RaffleResult> winners = raffleResultRepository.findByRaffleIdAndResult(raffleId, RaffleResultStatus.WIN);

        return winners.stream()
                .map(r -> new PublicRaffleResultResponse(
                        r.getUserId(),
                        r.getResult(),
                        r.getDecidedAt()
                ))
                .collect(Collectors.toList());
    }
}
