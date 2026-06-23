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
    REFUND_NOT_ALLOWED(HttpStatus.FORBIDDEN,"ORDER-004", "환불이 불가한 주문입니다."),
    NOT_PENDING_PAYMENT(HttpStatus.BAD_REQUEST, "ORDER-005", "결제 대기 상태의 주문만 확정할 수 있습니다."),
    NOT_CONFIRMED(HttpStatus.BAD_REQUEST,"ORDER-006", "확정된 주문만 배송을 시작할 수 있습니다."),
    NOT_SHIPPING(HttpStatus.BAD_REQUEST, "ORDER-007", "배송 중인 주문만 완료 처리할 수 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
