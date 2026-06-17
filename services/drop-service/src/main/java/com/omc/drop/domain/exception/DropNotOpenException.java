package com.omc.drop.domain.exception;

import com.omc.common.exception.BusinessException;
import com.omc.common.exception.ErrorCode;

public class DropNotOpenException extends BusinessException {
    public DropNotOpenException() {
        super(ErrorCode.DROP_NOT_OPEN);
    }
}
