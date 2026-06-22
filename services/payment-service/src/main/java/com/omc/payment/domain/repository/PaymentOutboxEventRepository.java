package com.omc.payment.domain.repository;

import com.omc.payment.domain.entity.PaymentOutboxEvent;
import com.omc.payment.domain.enums.OutboxEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PaymentOutboxEventRepository extends JpaRepository<PaymentOutboxEvent, UUID> {
    // 발행 대기 또는 재시도 대상 이벤트를 오래된 순서대로 100개씩 조회
    List<PaymentOutboxEvent> findTop100ByStatusInAndRetryCountLessThanOrderByCreatedAtAsc(
            Collection<OutboxEventStatus> status, int retryCount
    );
}
