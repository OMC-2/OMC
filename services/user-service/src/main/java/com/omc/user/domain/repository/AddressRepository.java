package com.omc.user.domain.repository;

import com.omc.user.domain.entity.Address;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AddressRepository extends JpaRepository<Address, UUID> {

    Page<Address> findByUserId(UUID userId, Pageable pageable);

    Optional<Address> findByAddressIdAndUserId(UUID addressId, UUID userId);

    @Modifying
    @Query("UPDATE Address a SET a.isDefault = FALSE WHERE a.userId = :userId")
    void unmarkAllDefaultByUserId(@Param("userId") UUID userId);
}
