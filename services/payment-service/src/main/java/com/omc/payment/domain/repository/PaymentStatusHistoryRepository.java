package com.omc.payment.domain.repository;

import com.omc.payment.domain.entity.PaymentStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentStatusHistoryRepository extends JpaRepository<PaymentStatusHistory, Integer> {
}
