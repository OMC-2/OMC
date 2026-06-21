package com.omc.payment.domain.repository;

import com.omc.payment.domain.entity.PaymentOutboxEvent;
import com.omc.payment.domain.enums.OutboxEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentOutboxEventRepository extends JpaRepository<PaymentOutboxEvent, UUID> {
    // 이벤트를 오래된 순서대로 100개씩 발행
    List<PaymentOutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(OutboxEventStatus status);
}
