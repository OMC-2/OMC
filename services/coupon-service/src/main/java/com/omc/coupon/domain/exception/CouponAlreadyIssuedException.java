package com.omc.coupon.domain.exception;

import com.omc.common.exception.BusinessException;

public class CouponAlreadyIssuedException extends BusinessException {
    public CouponAlreadyIssuedException() {
        super(CouponErrorCode.COUPON_ALREADY_ISSUED);
    }
}
