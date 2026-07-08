package com.omc.coupon.infrastructure.kafka;

public final class KafkaTopics {

    private KafkaTopics() {}

    // 구독 (Consumer)
    public static final String PAYMENT_COMPLETED     = "payment.completed";
    public static final String PAYMENT_FAILED        = "payment.failed";
    public static final String HOLD_EXPIRED          = "hold.expired";
    public static final String REFUND_DONE           = "refund.done";

    // 소비 실패 DLT
    public static final String PAYMENT_COMPLETED_DLT = "payment.completed.DLT";
    public static final String PAYMENT_FAILED_DLT    = "payment.failed.DLT";
    public static final String HOLD_EXPIRED_DLT      = "hold.expired.DLT";
    public static final String REFUND_DONE_DLT       = "refund.done.DLT";

    // 쿠폰 발급 요청 (Producer → Consumer 내부 처리)
    public static final String COUPON_ISSUE_REQUESTED     = "coupon.issue.requested";
    public static final String COUPON_ISSUE_REQUESTED_DLT = "coupon.issue.requested.DLT";

    // 발행 (Producer)
    public static final String COUPON_ISSUED     = "coupon.issued";
    public static final String COUPON_USED       = "coupon.used";
}
