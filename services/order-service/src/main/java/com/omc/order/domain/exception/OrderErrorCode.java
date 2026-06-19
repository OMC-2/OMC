package com.omc.order.domain.exception;

import com.omc.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum OrderErrorCode implements ErrorCode {

    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER-001", "주문을 찾을 수 없습니다."),
    INVALID_ORDER_STATE(HttpStatus.BAD_REQUEST, "0RDER-002", "유효하지 않은 주문 상태 전이 입니다."),
    ORDER_ALREADY_CANCELLED(HttpStatus.BAD_REQUEST,"ORDER-003", "이미 취소된 주문입니다."),
    REFUND_NOT_ALLOWED(HttpStatus.FORBIDDEN,"ORDER-004", "환불이 불가한 주문입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
