package com.omc.drop.presentation.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record DropUpdateRequest(
        @NotNull(message = "시작 시간은 필수입니다.")
        @FutureOrPresent(message = "시작 시간은 현재 또는 미래여야 합니다.")
        LocalDateTime startAt,

        @NotNull(message = "종료 시간은 필수입니다.")
        @FutureOrPresent(message = "종료 시간은 현재 또는 미래여야 합니다.")
        LocalDateTime endAt,

        @Positive(message = "재고 수량은 1 이상이어야 합니다.")
        int totalQty,

        @Positive(message = "선점 대기 시간은 1초 이상이어야 합니다.")
        int holdTtlSec
) {
}
