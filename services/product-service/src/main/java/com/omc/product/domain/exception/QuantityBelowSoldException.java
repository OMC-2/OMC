package com.omc.product.domain.exception;

import com.omc.common.exception.BusinessException;

public class QuantityBelowSoldException extends BusinessException {
    public QuantityBelowSoldException() {
        super(ProductErrorCode.QUANTITY_BELOW_SOLD);
    }
}
