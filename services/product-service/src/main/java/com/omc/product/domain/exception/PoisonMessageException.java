package com.omc.product.domain.exception;

/**
 * 역직렬화 실패 등, 재시도해도 절대 성공할 수 없는 메시지("poison message")에 사용
 * KafkaConsumerConfig의 errorHandler.addNotRetryableExceptions()에 등록되어
 * 재시도 없이 즉시 DLT로 이관
 * (payment.completed 이벤트의 orderId/userId/dropId가 UUID 형식이 아니어서
 * 컨슈머가 같은 오프셋에서 무한 재시도에 빠졌던 사고 재발 방지용)
 */
public class PoisonMessageException extends RuntimeException {
    public PoisonMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
