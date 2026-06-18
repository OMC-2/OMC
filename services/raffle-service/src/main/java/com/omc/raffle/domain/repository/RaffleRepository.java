package com.omc.raffle.domain.repository;

import com.omc.raffle.domain.entity.Raffle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RaffleRepository extends JpaRepository<Raffle, UUID> {
    // 특정 드롭에 대한 래플 이벤트 단건 조회
    Optional<Raffle> findByDropId(UUID dropId);
}
