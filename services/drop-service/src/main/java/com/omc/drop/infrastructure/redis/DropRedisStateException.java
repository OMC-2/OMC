package com.omc.drop.infrastructure.redis;

import java.util.UUID;

public class DropRedisStateException extends RuntimeException {
    private DropRedisStateException(String message) {
        super(message);
    }

    private DropRedisStateException(String message, Throwable cause) {
        super(message, cause);
    }

    public static DropRedisStateException missingHoldAfterPurchase(UUID dropId, UUID orderId) {
        return new DropRedisStateException(
                "Redis 선점 직후 hold score 조회 실패: holds 키에 orderId가 없습니다. "
                        + "dropId=" + dropId + ", orderId=" + orderId);
    }

    public static DropRedisStateException missingProductId(UUID dropId) {
        return new DropRedisStateException(
                "Redis productId 조회 실패: warmup 시 저장된 product_id 키가 없습니다. "
                        + "dropId=" + dropId);
    }

    public static DropRedisStateException invalidProductId(UUID dropId, String productId, Throwable cause) {
        return new DropRedisStateException(
                "Redis productId 형식 오류: product_id 값이 UUID 형식이 아닙니다. "
                        + "dropId=" + dropId + ", productId=" + productId,
                cause);
    }
}
