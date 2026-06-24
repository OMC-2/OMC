package com.omc.raffle.application.service;

import com.omc.common.exception.BusinessException;
import com.omc.common.response.PageResponse;
import com.omc.raffle.application.dto.response.RaffleEntryResponse;
import com.omc.raffle.application.dto.response.RaffleResponse;
import com.omc.raffle.domain.entity.Raffle;
import com.omc.raffle.domain.entity.RaffleEntry;
import com.omc.raffle.domain.exception.RaffleErrorCode;
import com.omc.raffle.domain.repository.RaffleEntryRepository;
import com.omc.raffle.domain.repository.RaffleRepository;
import com.omc.raffle.presentation.dto.request.admin.AdminRaffleCreateRequest;
import com.omc.raffle.presentation.dto.request.admin.AdminRaffleStatusUpdateRequest;
import com.omc.raffle.presentation.dto.request.admin.AdminRaffleUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminRaffleAppService {

    private final RaffleRepository raffleRepository;
    private final RaffleEntryRepository raffleEntryRepository;

    @Transactional
    public RaffleResponse createRaffle(AdminRaffleCreateRequest request) {
        Raffle raffle = Raffle.create(
                request.dropId(),
                request.name(),
                request.winnerCount(),
                request.startedAt(),
                request.endedAt()
        );
        Raffle savedRaffle = raffleRepository.save(raffle);
        return RaffleResponse.from(savedRaffle);
    }

    @Transactional
    public void updateRaffle(UUID raffleId, AdminRaffleUpdateRequest request) {
        Raffle raffle = raffleRepository.findById(raffleId)
                .orElseThrow(() -> new BusinessException(RaffleErrorCode.RAFFLE_001));
        
        raffle.update(request.name(), request.winnerCount());
    }

    @Transactional
    public void deleteRaffle(UUID raffleId) {
        Raffle raffle = raffleRepository.findById(raffleId)
                .orElseThrow(() -> new BusinessException(RaffleErrorCode.RAFFLE_001));
        
        raffle.delete();
    }

    @Transactional
    public void updateRaffleStatus(UUID raffleId, AdminRaffleStatusUpdateRequest request) {
        Raffle raffle = raffleRepository.findById(raffleId)
                .orElseThrow(() -> new BusinessException(RaffleErrorCode.RAFFLE_001));
        
        raffle.updateStatus(request.status());
    }

    public PageResponse<RaffleEntryResponse> getRaffleEntries(UUID raffleId, Pageable pageable) {
        // Validate if raffle exists
        if (!raffleRepository.existsById(raffleId)) {
            throw new BusinessException(RaffleErrorCode.RAFFLE_001);
        }
        Page<RaffleEntryResponse> page = raffleEntryRepository.findByRaffleId(raffleId, pageable)
                .map(RaffleEntryResponse::from);
        return new PageResponse<>(page);
    }
}
