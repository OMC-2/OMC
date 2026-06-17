package com.omc.raffle.domain.exception;

import com.omc.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum RaffleErrorCode implements ErrorCode {

    RAFFLE_NOT_FOUND(HttpStatus.NOT_FOUND, "RAFFLE-001", "응모 정보를 찾을 수 없습니다."),
    ALREADY_APPLIED(HttpStatus.CONFLICT, "RAFFLE-002", "이미 응모한 드롭입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
