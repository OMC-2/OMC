package com.omc.user.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateAddressRequest(
        @NotBlank(message = "수령인 이름은 필수입니다.")
        String recipientName,

        @NotBlank(message = "연락처는 필수입니다.")
        String phone,

        @NotBlank(message = "우편번호는 필수입니다.")
        String zipCode,

        @NotBlank(message = "주소는 필수입니다.")
        String address,

        String addressDetail,

        Boolean isDefault
) {}
