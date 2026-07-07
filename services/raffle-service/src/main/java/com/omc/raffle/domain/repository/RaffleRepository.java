package com.omc.raffle.domain.repository;

import com.omc.raffle.domain.entity.Raffle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import com.omc.raffle.domain.enums.RaffleStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RaffleRepository extends JpaRepository<Raffle, UUID> {
    @org.springframework.cache.annotation.Cacheable(cacheNames = "raffle", key = "#id")
    Optional<Raffle> findById(UUID id);
    // 추첨 스케줄러용: 종료 시간이 지났고 상태가 OPEN인 래플 목록 조회
    List<Raffle> findAllByStatusAndEndedAtBefore(RaffleStatus status, LocalDateTime now);

    // 오픈 스케줄러용: 시작 시간이 지났고 상태가 SCHEDULED인 래플 목록 조회
    List<Raffle> findAllByStatusAndStartedAtBefore(RaffleStatus status, LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.cache.annotation.CacheEvict(cacheNames = "raffle", key = "#id")
    @org.springframework.data.jpa.repository.Query("UPDATE Raffle r SET r.status = :newStatus WHERE r.id = :id AND r.status = com.omc.raffle.domain.enums.RaffleStatus.OPEN")
    int updateStatusIfOpen(@org.springframework.data.repository.query.Param("id") UUID id, @org.springframework.data.repository.query.Param("newStatus") RaffleStatus newStatus);
}
