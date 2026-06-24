package com.omc.drop.domain.exception;

import com.omc.common.exception.BusinessException;

public class InvalidDropQuantityException extends BusinessException {
    public InvalidDropQuantityException() {
        super(DropErrorCode.DROP_INVALID_QUANTITY);
    }
}
