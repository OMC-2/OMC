package com.omc.drop.domain.exception;

import com.omc.common.exception.BusinessException;
import com.omc.common.exception.ErrorCode;

public class InvalidDropStatusException extends BusinessException {
    public InvalidDropStatusException() {
        super(ErrorCode.DROP_INVALID_STATUS);
    }
}
