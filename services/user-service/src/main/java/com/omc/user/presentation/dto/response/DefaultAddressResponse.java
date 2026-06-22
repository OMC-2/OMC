package com.omc.user.presentation.dto.response;

import java.util.UUID;

public record DefaultAddressResponse(
        UUID addressId,
        boolean isDefault
) {}
