package com.omc.coupon.infrastructure.kafka;

public final class KafkaTopics {

    private KafkaTopics() {}

    // 구독 (Consumer)
    public static final String PAYMENT_COMPLETED = "payment.completed";
    public static final String PAYMENT_FAILED    = "payment.failed";
    public static final String HOLD_EXPIRED      = "hold.expired";
    public static final String REFUND_DONE       = "refund.done";

    // 발행 (Producer)
    public static final String COUPON_ISSUED     = "coupon.issued";
    public static final String COUPON_USED       = "coupon.used";
}
