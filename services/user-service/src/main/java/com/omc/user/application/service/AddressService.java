package com.omc.user.application.service;

import com.omc.common.response.PageResponse;
import com.omc.user.domain.entity.Address;
import com.omc.user.domain.exception.AddressNotFoundException;
import com.omc.user.domain.repository.AddressRepository;
import com.omc.user.presentation.dto.request.CreateAddressRequest;
import com.omc.user.presentation.dto.request.UpdateAddressRequest;
import com.omc.user.presentation.dto.response.AddressResponse;
import com.omc.user.presentation.dto.response.DefaultAddressResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository addressRepository;

    @Transactional
    public AddressResponse createAddress(UUID userId, CreateAddressRequest request) {
        boolean shouldBeDefault = Boolean.TRUE.equals(request.isDefault());
        if (shouldBeDefault) {
            addressRepository.unmarkAllDefaultByUserId(userId);
        }
        Address address = Address.create(
                userId,
                request.recipientName(),
                request.phone(),
                request.zipCode(),
                request.address(),
                request.addressDetail(),
                shouldBeDefault
        );
        return AddressResponse.from(addressRepository.save(address));
    }

    @Transactional(readOnly = true)
    public PageResponse<AddressResponse> getAddresses(UUID userId, Pageable pageable) {
        Page<AddressResponse> page = addressRepository.findByUserId(userId, pageable)
                .map(AddressResponse::from);
        return new PageResponse<>(page);
    }

    @Transactional(readOnly = true)
    public AddressResponse getAddress(UUID userId, UUID addressId) {
        Address address = addressRepository.findByAddressIdAndUserId(addressId, userId)
                .orElseThrow(AddressNotFoundException::new);
        return AddressResponse.from(address);
    }

    @Transactional
    public AddressResponse updateAddress(UUID userId, UUID addressId, UpdateAddressRequest request) {
        Address address = addressRepository.findByAddressIdAndUserId(addressId, userId)
                .orElseThrow(AddressNotFoundException::new);
        address.update(request.recipientName(), request.phone(), request.zipCode(),
                request.address(), request.addressDetail());
        return AddressResponse.from(address);
    }

    @Transactional
    public void deleteAddress(UUID userId, UUID addressId) {
        Address address = addressRepository.findByAddressIdAndUserId(addressId, userId)
                .orElseThrow(AddressNotFoundException::new);
        addressRepository.delete(address);
    }

    @Transactional
    public DefaultAddressResponse setDefaultAddress(UUID userId, UUID addressId) {
        addressRepository.unmarkAllDefaultByUserId(userId);
        Address target = addressRepository.findByAddressIdAndUserId(addressId, userId)
                .orElseThrow(AddressNotFoundException::new);
        target.markAsDefault();
        return new DefaultAddressResponse(target.getAddressId(), true);
    }
}
