package com.omc.raffle.domain.exception;

import com.omc.common.exception.BusinessException;
import com.omc.common.exception.ErrorCode;

public class RaffleNotFoundException extends BusinessException {

    public RaffleNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
    
    public RaffleNotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
