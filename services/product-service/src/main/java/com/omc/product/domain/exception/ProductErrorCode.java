package com.omc.product.domain.exception;

import com.omc.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProductErrorCode implements ErrorCode {

    // 상품
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT-001", "존재하지 않는 상품입니다."),
    PRODUCT_ALREADY_DELETED(HttpStatus.BAD_REQUEST, "PRODUCT-002", "이미 삭제된 상품입니다."),
    PRODUCT_HAS_ACTIVE_DROP(HttpStatus.CONFLICT, "PRODUCT-003", "진행 중인 드롭이 있어 삭제할 수 없습니다."),
    INVALID_PRICE(HttpStatus.BAD_REQUEST, "PRODUCT-004", "가격은 0보다 커야 합니다."),
    INVALID_QUANTITY(HttpStatus.BAD_REQUEST, "PRODUCT-005", "재고 수량은 1 이상이어야 합니다."),

    // 재고
    INVENTORY_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT-010", "재고 정보를 찾을 수 없습니다."),
    STOCK_DEDUCT_FAILED(HttpStatus.CONFLICT, "PRODUCT-011", "재고 차감에 실패했습니다. (동시성 경합)"),
    ALREADY_CONFIRMED(HttpStatus.CONFLICT, "PRODUCT-012", "이미 처리된 주문입니다."),
    ACTIVE_DROP_EXISTS(HttpStatus.CONFLICT, "PRODUCT-013", "진행 중인 드롭이 있어 수동 수정할 수 없습니다."),
    QUANTITY_BELOW_SOLD(HttpStatus.BAD_REQUEST, "PRODUCT-014", "총 재고는 판매 수량보다 적을 수 없습니다."),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "PRODUCT-015", "재고가 부족합니다."),
    DEDUCTION_BUSY(HttpStatus.SERVICE_UNAVAILABLE, "PRODUCT-016", "재고 차감 처리량이 많아 잠시 후 재시도가 필요합니다."),

    // 외부 서비스
    DROP_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "PRODUCT-020", "Drop Service가 응답하지 않습니다. 잠시 후 다시 시도해 주세요."),

    // Outbox
    OUTBOX_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT-030", "존재하지 않는 Outbox 이벤트입니다."),
    OUTBOX_EVENT_NOT_FAILED(HttpStatus.CONFLICT, "PRODUCT-031", "FAILED 상태의 이벤트만 재처리 가능합니다.");


    private final HttpStatus status;
    private final String code;
    private final String message;
}
