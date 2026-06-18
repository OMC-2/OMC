package com.omc.raffle.domain.repository;

import com.omc.raffle.domain.entity.RaffleEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RaffleEntryRepository extends JpaRepository<RaffleEntry, UUID> {
    
    // 특정 드롭에 유저가 응모한 내역 조회
    Optional<RaffleEntry> findByDropIdAndUserId(UUID dropId, UUID userId);
    
    // 유저 중복 응모 여부 확인용 (주의: Redis 선행 확인 필요)
    boolean existsByDropIdAndUserId(UUID dropId, UUID userId);
    
    // 특정 드롭의 전체 응모 내역 조회 (추첨 진행 시 사용)
    List<RaffleEntry> findAllByDropId(UUID dropId);
}
