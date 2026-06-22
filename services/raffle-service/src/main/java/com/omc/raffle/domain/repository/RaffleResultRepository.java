package com.omc.raffle.domain.repository;

import com.omc.raffle.domain.entity.RaffleResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RaffleResultRepository extends JpaRepository<RaffleResult, UUID> {
    
    // 특정 드롭의 추첨 결과(당첨/낙첨) 전체 조회
    List<RaffleResult> findAllByRaffleId(UUID raffleId);
    
    // 특정 응모권(entry)에 대한 추첨 결과 조회
    Optional<RaffleResult> findByEntryId(UUID entryId);

    // 유저의 특정 래플 추첨 결과 조회
    Optional<RaffleResult> findByRaffleIdAndUserId(UUID raffleId, UUID userId);
}
