package com.omc.raffle.domain.repository;

import com.omc.raffle.domain.entity.Raffle;
import org.springframework.data.jpa.repository.JpaRepository;

import com.omc.raffle.domain.enums.RaffleStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RaffleRepository extends JpaRepository<Raffle, UUID> {
    // 추첨 스케줄러용: 종료 시간이 지났고 상태가 OPEN인 래플 목록 조회
    List<Raffle> findAllByStatusAndEndedAtBefore(RaffleStatus status, LocalDateTime now);

    // 오픈 스케줄러용: 시작 시간이 지났고 상태가 SCHEDULED인 래플 목록 조회
    List<Raffle> findAllByStatusAndStartedAtBefore(RaffleStatus status, LocalDateTime now);
}
