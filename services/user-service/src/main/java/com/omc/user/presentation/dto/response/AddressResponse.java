package com.omc.user.presentation.dto.response;

import com.omc.user.domain.entity.Address;

import java.util.UUID;

public record AddressResponse(
        UUID addressId,
        String recipientName,
        String phone,
        String zipCode,
        String address,
        String addressDetail,
        boolean isDefault
) {
    public static AddressResponse from(Address address) {
        return new AddressResponse(
                address.getAddressId(),
                address.getRecipientName(),
                address.getPhone(),
                address.getZipCode(),
                address.getAddress(),
                address.getAddressDetail(),
                address.isDefault()
        );
    }
}
