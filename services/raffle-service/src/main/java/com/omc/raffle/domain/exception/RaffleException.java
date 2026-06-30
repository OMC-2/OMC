package com.omc.raffle.domain.exception;

import com.omc.raffle.domain.enums.RaffleErrorCode;

import lombok.Getter;

@Getter
public class RaffleException extends RuntimeException {

    private final RaffleErrorCode errorCode;

    public RaffleException(RaffleErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public RaffleException(RaffleErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
