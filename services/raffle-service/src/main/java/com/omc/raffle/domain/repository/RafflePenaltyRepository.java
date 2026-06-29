package com.omc.raffle.domain.repository;

import com.omc.raffle.domain.entity.RafflePenalty;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface RafflePenaltyRepository extends JpaRepository<RafflePenalty, UUID> {
}
