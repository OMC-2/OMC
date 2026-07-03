package com.omc.product.infrastructure.config;

import com.omc.product.domain.exception.PoisonMessageException;
import com.omc.product.infrastructure.kafka.KafkaTopics;
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
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * payment.completed 컨슈머의 재시도/DLT 정책
 *
 * 배경: 오늘 k6 부하테스트 중 payment.completed 이벤트의 orderId/userId/dropId가
 * UUID 형식이 아니어서 역직렬화가 계속 실패, 컨슈머가 같은 오프셋에서 무한 재시도에
 * 빠져 해당 파티션의 이후 정상 메시지 처리까지 막혔던 사고가 있었음
 * (Kafka 컨슈머 그룹 오프셋을 수동으로 리셋해서 복구)
 *
 * 재발 방지를 위해:
 * 1. 최대 재시도 횟수를 명시적으로 제한하고, 그 이상 실패하면 DLT로 이관
 * 2. 역직렬화 실패처럼 재시도해도 절대 성공할 수 없는 경우(PoisonMessageException)는
 *    재시도 자체를 생략하고 즉시 DLT로 이관
 * → 문제 메시지 1건이 파티션 전체를 막는 상황을 방지
 */
@Slf4j
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public CommonErrorHandler kafkaCommonErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${product.kafka.consumer.retry.interval-ms:1000}") long retryIntervalMs,
            @Value("${product.kafka.consumer.retry.max-attempts:3}") long maxAttempts
    ) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(resolveDltTopic(record), record.partition())
        );

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(retryIntervalMs, Math.max(0, maxAttempts - 1))
        );

        // 포이즌 필(역직렬화 실패 등)은 재시도 없이 바로 DLT
        errorHandler.addNotRetryableExceptions(PoisonMessageException.class);

        // DLT로 이관된 레코드는 offset도 함께 커밋 → 같은 실패 메시지 무한 반복 소비 방지
        errorHandler.setCommitRecovered(true);

        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("[KafkaConsumerConfig] 재시도 중. topic={}, key={}, attempt={}, error={}",
                        record.topic(), record.key(), deliveryAttempt, ex.getMessage())
        );
        return errorHandler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
            ConsumerFactory<Object, Object> consumerFactory,
            CommonErrorHandler kafkaCommonErrorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaCommonErrorHandler);
        // concurrency는 여기서 강제하지 않음 — 각 @KafkaListener의 concurrency 속성을 그대로 사용
        // (PaymentCompletedConsumer는 명시적으로 concurrency="1" 고정, 근거는 해당 클래스 주석 참고)
        return factory;
    }

    private String resolveDltTopic(ConsumerRecord<?, ?> record) {
        if (KafkaTopics.PAYMENT_COMPLETED.equals(record.topic())) {
            return KafkaTopics.PAYMENT_COMPLETED_DLT;
        }
        return record.topic() + ".DLT";
    }
}
