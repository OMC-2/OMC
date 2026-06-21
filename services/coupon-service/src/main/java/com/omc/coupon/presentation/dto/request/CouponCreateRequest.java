package com.omc.coupon.presentation.dto.request;

import com.omc.coupon.domain.entity.Coupon;
import com.omc.coupon.domain.enums.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CouponCreateRequest(
        @NotBlank String name,
        @NotNull DiscountType discountType,
        @NotNull @DecimalMin("0.01") BigDecimal discountValue,
        BigDecimal maxDiscountAmount,
        @Min(1) int totalQuantity,
        @NotNull LocalDateTime startedAt,
        @NotNull LocalDateTime expiredAt
) {
    public Coupon toEntity() {
        return Coupon.builder()
                .name(name)
                .discountType(discountType)
                .discountValue(discountValue)
                .maxDiscountAmount(maxDiscountAmount)
                .totalQuantity(totalQuantity)
                .startedAt(startedAt)
                .expiredAt(expiredAt)
                .build();
    }
}
