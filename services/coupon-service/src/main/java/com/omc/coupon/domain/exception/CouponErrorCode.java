package com.omc.coupon.domain.exception;

import com.omc.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CouponErrorCode implements ErrorCode {

    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "COUPON-001", "존재하지 않는 쿠폰입니다."),
    USER_COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "COUPON-002", "보유하지 않은 쿠폰입니다."),
    COUPON_ALREADY_ISSUED(HttpStatus.CONFLICT, "COUPON-003", "이미 발급받은 쿠폰입니다."),
    COUPON_OUT_OF_STOCK(HttpStatus.CONFLICT, "COUPON-004", "쿠폰 재고가 소진되었습니다."),
    COUPON_NOT_AVAILABLE(HttpStatus.CONFLICT, "COUPON-005", "사용 불가능한 쿠폰입니다. (이미 사용 중이거나 예약 중)"),
    COUPON_EXPIRED(HttpStatus.BAD_REQUEST, "COUPON-006", "만료된 쿠폰입니다."),
    COUPON_NOT_STARTED(HttpStatus.BAD_REQUEST, "COUPON-007", "아직 발급 기간이 시작되지 않은 쿠폰입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
