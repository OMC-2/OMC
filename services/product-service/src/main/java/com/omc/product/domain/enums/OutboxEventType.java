package com.omc.product.domain.enums;

public enum OutboxEventType {
    STOCK_DEDUCTED, // DB 재고 확정 차감 완료
    STOCK_FAILED    // DB 재고 차감 실패
}
