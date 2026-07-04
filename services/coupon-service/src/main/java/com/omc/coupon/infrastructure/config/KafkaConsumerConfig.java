package com.omc.coupon.infrastructure.config;

import com.omc.coupon.infrastructure.kafka.KafkaTopics;
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
            @Value("${coupon.kafka.consumer.retry.interval-ms:1000}") long retryIntervalMs,
            @Value("${coupon.kafka.consumer.retry.max-attempts:3}") long maxAttempts
    ) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(resolveDltTopic(record), record.partition())
        );

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(retryIntervalMs, Math.max(0, maxAttempts - 1))
        );

        // 역직렬화 실패 등 포이즌 필은 재시도 없이 바로 DLT
        errorHandler.addNotRetryableExceptions(IllegalStateException.class, IllegalArgumentException.class);

        // DLT로 이관된 레코드는 offset도 함께 커밋 — 동일 실패 메시지 무한 반복 방지
        errorHandler.setCommitRecovered(true);

        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("카프카 메시지 재시도 중입니다. topic={}, key={}, attempt={}",
                        record.topic(), record.key(), deliveryAttempt, ex)
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
        factory.setBatchListener(true); // 배치 리스너 명시적 활성화

        // MANUAL_IMMEDIATE: listener 내부에서 acknowledge() 호출 시점에 바로 offset 커밋
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }

    private String resolveDltTopic(ConsumerRecord<?, ?> record) {
        return switch (record.topic()) {
            case KafkaTopics.PAYMENT_COMPLETED        -> KafkaTopics.PAYMENT_COMPLETED_DLT;
            case KafkaTopics.PAYMENT_FAILED           -> KafkaTopics.PAYMENT_FAILED_DLT;
            case KafkaTopics.HOLD_EXPIRED             -> KafkaTopics.HOLD_EXPIRED_DLT;
            case KafkaTopics.REFUND_DONE              -> KafkaTopics.REFUND_DONE_DLT;
            case KafkaTopics.COUPON_ISSUE_REQUESTED   -> KafkaTopics.COUPON_ISSUE_REQUESTED_DLT;
            default -> record.topic() + ".DLT";
        };
    }
}
