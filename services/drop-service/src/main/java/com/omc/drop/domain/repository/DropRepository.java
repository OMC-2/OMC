package com.omc.drop.domain.repository;

import com.omc.drop.domain.entity.Drop;
import com.omc.drop.domain.enums.DropStatus;
import com.omc.drop.domain.exception.DropNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DropRepository extends JpaRepository<Drop, UUID> {

    Page<Drop> findAllByStatus(DropStatus status, Pageable pageable);

    default Drop getByIdOrThrow(UUID dropId) {
        return findById(dropId).orElseThrow(DropNotFoundException::new);
    }
}
