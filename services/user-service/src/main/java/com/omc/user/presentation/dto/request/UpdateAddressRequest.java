package com.omc.user.presentation.dto.request;

public record UpdateAddressRequest(
        String recipientName,
        String phone,
        String zipCode,
        String address,
        String addressDetail
) {}
