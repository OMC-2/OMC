package com.omc.notification.infrastructure.kafka;

public final class KafkaTopics {

    private KafkaTopics() {}

    public static final String DROP_OPENED          = "drop.opened";
    public static final String ORDER_CONFIRMED      = "order.confirmed";
    public static final String ORDER_CANCELLED      = "order.cancelled";
    public static final String ORDER_SHIPPED        = "order.shipped";
    public static final String RAFFLE_WINNER        = "raffle.winner.selected";
    public static final String RAFFLE_LOSER         = "raffle.loser.notified";
    public static final String COUPON_ISSUED        = "coupon.issued";
    public static final String COUPON_USED          = "coupon.used";
    public static final String REFUND_DONE          = "refund.done";
    public static final String PAYMENT_FAILED       = "payment.failed";

    // 소비 실패 DLT
    public static final String DROP_OPENED_DLT      = "drop.opened.DLT";
    public static final String ORDER_CONFIRMED_DLT  = "order.confirmed.DLT";
    public static final String ORDER_CANCELLED_DLT  = "order.cancelled.DLT";
    public static final String ORDER_SHIPPED_DLT    = "order.shipped.DLT";
    public static final String RAFFLE_WINNER_DLT    = "raffle.winner.selected.DLT";
    public static final String RAFFLE_LOSER_DLT     = "raffle.loser.notified.DLT";
    public static final String COUPON_ISSUED_DLT    = "coupon.issued.DLT";
    public static final String COUPON_USED_DLT      = "coupon.used.DLT";
    public static final String REFUND_DONE_DLT      = "refund.done.DLT";
    public static final String PAYMENT_FAILED_DLT   = "payment.failed.DLT";
}
