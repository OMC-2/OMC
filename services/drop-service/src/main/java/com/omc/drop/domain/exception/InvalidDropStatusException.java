package com.omc.drop.domain.exception;

import com.omc.common.exception.BusinessException;

public class InvalidDropStatusException extends BusinessException {
    public InvalidDropStatusException() {
        super(DropErrorCode.DROP_INVALID_STATUS);
    }
}
