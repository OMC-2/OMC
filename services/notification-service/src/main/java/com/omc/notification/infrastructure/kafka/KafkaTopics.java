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
    public static final String REFUND_DONE          = "refund.done";
}
