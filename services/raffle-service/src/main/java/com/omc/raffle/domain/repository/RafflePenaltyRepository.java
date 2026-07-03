package com.omc.raffle.domain.repository;

import com.omc.raffle.domain.entity.RafflePenalty;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

import java.time.LocalDateTime;

public interface RafflePenaltyRepository extends JpaRepository<RafflePenalty, UUID> {
    boolean existsByUserIdAndPenaltyEndDateAfter(UUID userId, LocalDateTime now);
}
