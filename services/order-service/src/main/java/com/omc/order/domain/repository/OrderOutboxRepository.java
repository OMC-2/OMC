package com.omc.order.domain.repository;

import com.omc.order.domain.entity.OrderOutboxEvent;
import com.omc.order.domain.enums.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderOutboxRepository extends JpaRepository<OrderOutboxEvent, UUID> {
  //발행 대기(INIT) 레코드를 생성순으로 조회 (오래된 것 먼저)
  //Poller가 배치 크기만큼 가져가서 발생 (status, created_at) 복합 인덱스 대상
  List<OrderOutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);
}
