package com.omc.product.domain.exception;

import com.omc.common.exception.BusinessException;

public class DropServiceUnavailableException extends BusinessException {
    public DropServiceUnavailableException() {
        super(ProductErrorCode.DROP_SERVICE_UNAVAILABLE);
    }
}
