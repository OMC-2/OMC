package com.omc.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INTERNAL_SERVER_ERROR(500, "C001", "내부 서버 오류가 발생했습니다."),
    INVALID_INPUT_VALUE(400, "C002", "유효하지 않은 입력값입니다."),
    RESOURCE_NOT_FOUND(404, "C003", "요청한 리소스를 찾을 수 없습니다."),
    UNAUTHORIZED(401, "C004", "인증이 필요합니다."),
    FORBIDDEN(403, "C005", "접근 권한이 없습니다."),

    // User
    USER_NOT_FOUND(404, "U001", "사용자를 찾을 수 없습니다."),
    DUPLICATE_EMAIL(409, "U002", "이미 사용 중인 이메일입니다."),
    INVALID_PASSWORD(400, "U003", "비밀번호가 올바르지 않습니다."),
    INVALID_TOKEN(401, "U004", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(401, "U005", "만료된 토큰입니다."),

    // Product
    PRODUCT_NOT_FOUND(404, "P001", "상품을 찾을 수 없습니다."),
    INSUFFICIENT_STOCK(409, "P002", "재고가 부족합니다."),

    // Drop
    DROP_NOT_FOUND(404, "D001", "드롭을 찾을 수 없습니다."),
    DROP_NOT_OPEN(400, "D002", "드롭이 오픈 상태가 아닙니다."),

    // Raffle
    RAFFLE_NOT_FOUND(404, "R001", "응모 정보를 찾을 수 없습니다."),
    ALREADY_APPLIED(409, "R002", "이미 응모한 드롭입니다."),

    // Order
    ORDER_NOT_FOUND(404, "O001", "주문을 찾을 수 없습니다."),

    // Payment
    PAYMENT_NOT_FOUND(404, "PAY001", "결제 정보를 찾을 수 없습니다."),
    PAYMENT_FAILED(400, "PAY002", "결제에 실패했습니다.");

    private final int status;
    private final String code;
    private final String message;
}
