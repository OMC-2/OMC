package com.omc.product.infrastructure.kafka;

public final class KafkaTopics {
    private KafkaTopics() {}

    // 소비 토픽
    public static final String PAYMENT_COMPLETED = "payment.completed";

    // 발행 토픽
    public static final String STOCK_DEDUCTED = "stock.deducted";
    public static final String STOCK_FAILED   = "stock.failed";
}
