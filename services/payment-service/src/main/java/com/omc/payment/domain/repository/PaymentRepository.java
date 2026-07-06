package com.omc.payment.domain.repository;

import com.omc.payment.domain.entity.Payment;
import com.omc.payment.domain.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByOrderId(UUID orderId);

    Page<Payment> findAllByUserId(UUID userId, Pageable pageable);

    // UNKNOWN 상태 결제를 오래된 수정 순서로 조회
    List<Payment> findByPaymentStatusInOrderByUpdatedAtAsc(
            Collection<PaymentStatus> paymentStatuses,
            Pageable pageable
    );
}
