package com.omc.payment.domain.enums;

public enum PaymentStatus {
    READY,
    CONFIRMING,
    PAID,
    REFUNDED,
    FAILED,
    CANCELED,
    UNKNOWN; // 망 취소, 타임 아웃, 연동 장애

    public boolean canChangeTo(PaymentStatus next) {
        return switch (this) {
            case READY -> next == CONFIRMING || next == CANCELED;
            case CONFIRMING -> next == PAID || next == FAILED || next == UNKNOWN;
            case PAID -> next == REFUNDED;
            case UNKNOWN -> next == PAID || next == FAILED || next == CANCELED;
            case REFUNDED, FAILED, CANCELED -> false;
        };
    }
}
