package com.omc.drop.domain.repository;

import com.omc.drop.domain.entity.DropProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DropProcessedEventRepository extends JpaRepository<DropProcessedEvent, String> {
}
