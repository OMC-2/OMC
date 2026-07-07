package com.omc.order.infrastructure.kafka;

import com.omc.order.domain.entity.OrderDlqMessage;
import com.omc.order.domain.repository.OrderDlqRepository;
import feign.FeignException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class RdbmsDlqRecoverer implements ConsumerRecordRecoverer {

  private final OrderDlqRepository dlqRepository;
  private final Counter consumerDlqCounter; //Kafka consumer 처리 실패로 DLQ에 적재된 누적 수

  public RdbmsDlqRecoverer(OrderDlqRepository dlqRepository, MeterRegistry meterRegistry) {
    this.dlqRepository = dlqRepository;
    this.consumerDlqCounter = Counter.builder("order.consumer.dlq")
        .description("Kafka consumer 처리 실패로 RDBMS DLQ에 적재된 누적 수")
        .register(meterRegistry);
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void accept(ConsumerRecord<?,?> record, Exception exception) {
    //value-deserializer가 StringDeserializer 이므로 value는 항상 String(JSON)임
    String topic = record.topic();
    String key = (record.key() == null ) ? null : String.valueOf(record.key());
    String payload = (record.value() == null) ? null : String.valueOf(record.value());

    //recoverer 단계의 예외는 ListenerExecutionFailedException으로 한 번 감싸여 오므로, 진단에는 cause가 더 유용함
    Throwable rootCause = (exception != null && exception.getCause() != null) ? exception.getCause() : exception;

    OrderDlqMessage dlq = OrderDlqMessage.ofFailure(
        topic, key, payload, rootCause, record.partition(), record.offset());

    dlqRepository.save(dlq);
    consumerDlqCounter.increment(); //DLQ 적재 성공 시 메트릭 증가 (AI 진단/모니터링용)

    String causeMessage = (rootCause == null) ? "null" : rootCause.getMessage();
    String feignBody = (rootCause instanceof FeignException fe) ? fe.contentUTF8() : null;

    log.error("[DLQ 적재] topic={}, partition={}, offset={}, errorClass={}, message={}, feignBody={}",
        topic, record.partition(), record.offset(),
        (rootCause == null ? "null" : rootCause.getClass().getSimpleName()),
        causeMessage, feignBody);
  }
}
