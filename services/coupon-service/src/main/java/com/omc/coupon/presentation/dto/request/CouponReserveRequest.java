package com.omc.coupon.presentation.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CouponReserveRequest(
        @NotNull UUID userCouponId,
        @NotNull UUID orderId,
        @NotNull UUID userId
) {}
