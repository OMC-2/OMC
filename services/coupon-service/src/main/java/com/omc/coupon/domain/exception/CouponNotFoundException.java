package com.omc.coupon.domain.exception;

import com.omc.common.exception.BusinessException;

public class CouponNotFoundException extends BusinessException {
    public CouponNotFoundException() {
        super(CouponErrorCode.COUPON_NOT_FOUND);
    }
}
