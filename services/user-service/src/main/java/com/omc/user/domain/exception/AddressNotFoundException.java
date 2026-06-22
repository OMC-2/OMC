package com.omc.user.domain.exception;

import com.omc.common.exception.BusinessException;

public class AddressNotFoundException extends BusinessException {
    public AddressNotFoundException() {
        super(UserErrorCode.ADDRESS_NOT_FOUND);
    }
}
