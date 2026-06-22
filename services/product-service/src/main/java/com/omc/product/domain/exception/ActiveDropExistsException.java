package com.omc.product.domain.exception;

import com.omc.common.exception.BusinessException;

public class ActiveDropExistsException extends BusinessException {

    public ActiveDropExistsException() {
        super(ProductErrorCode.ACTIVE_DROP_EXISTS);
    }
}
