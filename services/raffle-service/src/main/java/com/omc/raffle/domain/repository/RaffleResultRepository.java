package com.omc.raffle.domain.repository;

import com.omc.raffle.domain.entity.RaffleResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RaffleResultRepository extends JpaRepository<RaffleResult, UUID> {
    
    // 특정 드롭의 추첨 결과(당첨/낙첨) 전체 조회
    List<RaffleResult> findAllByRaffleId(UUID raffleId);

    List<RaffleResult> findByRaffleIdAndResult(UUID raffleId, com.omc.raffle.domain.enums.RaffleResultStatus result);
    
    // 특정 응모권(entry)에 대한 추첨 결과 조회
    Optional<RaffleResult> findByEntryId(UUID entryId);

    // 유저의 특정 래플 추첨 결과 조회
    Optional<RaffleResult> findByRaffleIdAndUserId(UUID raffleId, UUID userId);

    // 결제 실패 시 예비 당첨자를 위해 DB 레벨에서 무작위 낙첨자 1명 추출 (OOM 방지)
    @org.springframework.data.jpa.repository.Query(value = "SELECT * FROM p_raffle_results r WHERE r.raffle_id = :raffleId AND r.result = 'LOSE' ORDER BY random() LIMIT 1", nativeQuery = true)
    Optional<RaffleResult> findRandomLoserByRaffleId(@org.springframework.data.repository.query.Param("raffleId") UUID raffleId);
}
