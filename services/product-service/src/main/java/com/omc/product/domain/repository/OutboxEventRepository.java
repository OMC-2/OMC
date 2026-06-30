package com.omc.product.domain.repository;

import com.omc.product.domain.entity.OutboxEvent;
import com.omc.product.domain.enums.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    // Poller: INIT 상태 레코드를 생성 시간 순으로 조회
    List<OutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);

    // Admin: FAILED 상태 전체 조회
    List<OutboxEvent> findByStatus(OutboxStatus status);
}