package com.omc.drop.domain.exception;

import com.omc.common.exception.BusinessException;

public class InvalidDropHoldTtlException extends BusinessException {
    public InvalidDropHoldTtlException() {
        super(DropErrorCode.DROP_INVALID_HOLD_TTL);
    }
}
