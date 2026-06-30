package com.omc.raffle.domain.repository;

import com.omc.raffle.domain.entity.RaffleEntry;
import com.omc.raffle.domain.projection.RaffleEntryProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RaffleEntryRepository extends JpaRepository<RaffleEntry, UUID> {
    
    // 특정 드롭에 유저가 응모한 내역 조회
    Optional<RaffleEntry> findByRaffleIdAndUserId(UUID raffleId, UUID userId);
    
    // 유저 중복 응모 여부 확인용 (주의: Redis 선행 확인 필요)
    boolean existsByRaffleIdAndUserId(UUID raffleId, UUID userId);
    
    // 특정 드롭의 전체 응모 내역 조회 (추첨 진행 시 사용)
    List<RaffleEntry> findAllByRaffleId(UUID raffleId);

    // OOM 방지를 위해 ID와 UserID만 조회 (추첨 셔플용)
    @org.springframework.data.jpa.repository.Query("SELECT r.id as id, r.userId as userId FROM RaffleEntry r WHERE r.raffleId = :raffleId")
    List<RaffleEntryProjection> findProjectionsByRaffleId(@Param("raffleId") UUID raffleId);

    long countByRaffleId(UUID raffleId);

    // 내 응모 내역 조회 (페이징)
    Page<RaffleEntry> findByUserId(UUID userId, Pageable pageable);

    // 응모자 목록 조회 (페이징)
    Page<RaffleEntry> findByRaffleId(UUID raffleId, Pageable pageable);
}
