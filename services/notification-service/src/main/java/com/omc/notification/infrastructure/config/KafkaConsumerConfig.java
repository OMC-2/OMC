package com.omc.notification.infrastructure.config;

import com.omc.notification.infrastructure.kafka.KafkaTopics;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public CommonErrorHandler kafkaCommonErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${notification.kafka.consumer.retry.interval-ms:1000}") long retryIntervalMs,
            @Value("${notification.kafka.consumer.retry.max-attempts:3}") long maxAttempts
    ) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(resolveDltTopic(record), record.partition())
        );

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(retryIntervalMs, Math.max(0, maxAttempts - 1))
        );

        errorHandler.addNotRetryableExceptions(IllegalStateException.class, IllegalArgumentException.class);
        errorHandler.setCommitRecovered(true);
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("[KafkaConsumerConfig] 재시도. topic={}, offset={}, attempt={}, error={}",
                        record.topic(), record.offset(), deliveryAttempt, ex.getMessage())
        );

        return errorHandler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
            ConsumerFactory<Object, Object> consumerFactory,
            CommonErrorHandler kafkaCommonErrorHandler,
            @Value("${spring.kafka.listener.concurrency:3}") int concurrency
    ) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaCommonErrorHandler);
        factory.setConcurrency(concurrency);
        factory.setBatchListener(true);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }

    private String resolveDltTopic(ConsumerRecord<?, ?> record) {
        return switch (record.topic()) {
            case KafkaTopics.DROP_OPENED     -> KafkaTopics.DROP_OPENED_DLT;
            case KafkaTopics.ORDER_CONFIRMED -> KafkaTopics.ORDER_CONFIRMED_DLT;
            case KafkaTopics.ORDER_CANCELLED -> KafkaTopics.ORDER_CANCELLED_DLT;
            case KafkaTopics.ORDER_SHIPPED   -> KafkaTopics.ORDER_SHIPPED_DLT;
            case KafkaTopics.RAFFLE_WINNER   -> KafkaTopics.RAFFLE_WINNER_DLT;
            case KafkaTopics.RAFFLE_LOSER    -> KafkaTopics.RAFFLE_LOSER_DLT;
            case KafkaTopics.COUPON_ISSUED   -> KafkaTopics.COUPON_ISSUED_DLT;
            case KafkaTopics.COUPON_USED     -> KafkaTopics.COUPON_USED_DLT;
            case KafkaTopics.REFUND_DONE     -> KafkaTopics.REFUND_DONE_DLT;
            case KafkaTopics.PAYMENT_FAILED  -> KafkaTopics.PAYMENT_FAILED_DLT;
            default -> record.topic() + ".DLT";
        };
    }
}
