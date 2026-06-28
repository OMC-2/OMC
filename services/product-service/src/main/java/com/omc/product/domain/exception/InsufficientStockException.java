package com.omc.product.domain.exception;

import com.omc.common.exception.BusinessException;

public class InsufficientStockException extends BusinessException {
    public InsufficientStockException() {
        super(ProductErrorCode.INSUFFICIENT_STOCK);
    }
}