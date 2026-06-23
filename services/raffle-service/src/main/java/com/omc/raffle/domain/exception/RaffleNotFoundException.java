package com.omc.raffle.domain.exception;

public class RaffleNotFoundException extends RaffleException {

    public RaffleNotFoundException() {
        super(RaffleErrorCode.RAFFLE_001);
    }
}
