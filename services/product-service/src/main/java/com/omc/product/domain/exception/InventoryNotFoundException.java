package com.omc.product.domain.exception;

import com.omc.common.exception.BusinessException;

public class InventoryNotFoundException extends BusinessException {
    public InventoryNotFoundException() {
        super(ProductErrorCode.INVENTORY_NOT_FOUND);
    }
}