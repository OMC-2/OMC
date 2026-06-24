package com.omc.order.infrastructure.config;

import com.omc.order.domain.exception.OrderNotFoundException;
import com.omc.order.infrastructure.kafka.RdbmsDlqRecoverer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {

  @Bean
  public DefaultErrorHandler kafkaErrorHandler(RdbmsDlqRecoverer rdbmsDlqRecoverer) {
    //2초 간격 3회 재시도 후 recoverer로 전달
    FixedBackOff backOff = new FixedBackOff(2_000L, 3L);

    //기본 recoverer는 로그만 남김
    //TODO: 운영 시 RDBMS DLQ(FAILED 상태로 저장 + Admin 재발행 API) writer로 교체
    DefaultErrorHandler handler = new DefaultErrorHandler(rdbmsDlqRecoverer, backOff);

    //재시도 무의미 -> 첫 실패 즉시 recoverer(DLQ)로
    handler.addNotRetryableExceptions(
        OrderNotFoundException.class,
        MessageConversionException.class
    );
    return handler;
    }
  }

