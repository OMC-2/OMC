package com.omc.payment.infrastructure.config;

public final class KafkaTopics {

    // 구독 토픽
    public static final String ORDER_CREATED = "order.created";
    public static final String REFUND_REQUESTED = "refund.requested";
    public static final String STOCK_FAILED = "stock.failed";
    public static final String RAFFLE_WINNER_SELECTED = "raffle.winner.selected";

    // 발행 토픽
    public static final String PAYMENT_COMPLETED = "payment.completed";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String REFUND_DONE = "refund.done";

    private KafkaTopics() {
    }
}
