package com.omc.product.domain.repository;

import com.omc.product.domain.entity.FailedEventLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FailedEventLogRepository extends JpaRepository<FailedEventLog, UUID> {
}