package com.omc.drop.domain.exception;

import com.omc.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum DropErrorCode implements ErrorCode {

    DROP_NOT_FOUND(HttpStatus.NOT_FOUND, "DROP-001", "드롭을 찾을 수 없습니다."),
    DROP_NOT_OPEN(HttpStatus.CONFLICT, "DROP-002", "드롭이 오픈 상태가 아닙니다."),
    DROP_INVALID_STATUS(HttpStatus.BAD_REQUEST, "DROP-003", "현재 상태에서 허용되지 않는 작업입니다."),
    DROP_SOLD_OUT(HttpStatus.CONFLICT, "DROP-004", "재고가 소진되었습니다."),
    DROP_DUPLICATE_PURCHASE(HttpStatus.CONFLICT, "DROP-005", "이미 구매를 신청한 드롭입니다."),
    DROP_INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "DROP-006", "종료 시간은 시작 시간보다 이후여야 합니다."),
    DROP_INVALID_QUANTITY(HttpStatus.BAD_REQUEST, "DROP-007", "총 수량은 0보다 커야 합니다."),
    DROP_INVALID_HOLD_TTL(HttpStatus.BAD_REQUEST, "DROP-008", "홀드 유효 시간은 0보다 커야 합니다."),
    PURCHASE_RESERVATION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "DROP-009", "구매 선점 처리에 실패했습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
