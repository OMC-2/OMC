package com.omc.user.domain.repository;

import com.omc.user.domain.entity.Address;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddressRepository extends JpaRepository<Address, UUID> {

    Page<Address> findByUserId(UUID userId, Pageable pageable);

    Optional<Address> findByAddressIdAndUserId(UUID addressId, UUID userId);

    List<Address> findAllByUserId(UUID userId);
}
