package com.omc.product.domain.exception;

import com.omc.common.exception.BusinessException;

public class ProductAlreadyDeletedException extends BusinessException {
    public ProductAlreadyDeletedException() {
        super(ProductErrorCode.PRODUCT_ALREADY_DELETED);
    }
}