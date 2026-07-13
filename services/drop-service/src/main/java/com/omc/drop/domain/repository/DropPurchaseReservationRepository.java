package com.omc.drop.domain.repository;

import com.omc.drop.domain.entity.DropPurchaseReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DropPurchaseReservationRepository extends JpaRepository<DropPurchaseReservation, UUID> {
}
