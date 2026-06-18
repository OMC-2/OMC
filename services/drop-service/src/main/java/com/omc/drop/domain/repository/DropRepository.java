package com.omc.drop.domain.repository;

import com.omc.drop.domain.entity.Drop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DropRepository extends JpaRepository<Drop, UUID> {
}
