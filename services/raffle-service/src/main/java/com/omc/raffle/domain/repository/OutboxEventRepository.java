package com.omc.raffle.domain.repository;

import com.omc.raffle.domain.entity.OutboxEvent;
import com.omc.raffle.domain.enums.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findTop100ByStatusIn(List<OutboxStatus> statuses);
    List<OutboxEvent> findByStatusIn(List<OutboxStatus> statuses);
}
