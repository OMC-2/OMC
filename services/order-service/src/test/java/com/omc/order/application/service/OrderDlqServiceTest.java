package com.omc.order.application.service;

import com.omc.order.application.event.producer.OrderEventProducer;
import com.omc.order.domain.entity.OrderDlqMessage;
import com.omc.order.domain.enums.DlqStatus;
import com.omc.order.domain.repository.OrderDlqRepository;
import com.omc.order.presentation.dto.response.DlqMessageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrderDlqService 단위 테스트.
 * service 패키지 커버리지(jacoco 0.80) 충족을 위해 전 메서드 커버.
 */
@ExtendWith(MockitoExtension.class)
class OrderDlqServiceTest {

  @Mock OrderDlqRepository dlqRepository;
  @Mock OrderEventProducer orderEventProducer;

  @InjectMocks OrderDlqService dlqService;

  private OrderDlqMessage failedDlq() {
    return OrderDlqMessage.ofFailure(
        "purchase.confirmed", "key-1", "{\"eventId\":\"e1\"}",
        new RuntimeException("boom"), 0, 10L);
  }

  @Test
  @DisplayName("getMessages: 상태별 DLQ를 페이지로 조회해 응답 DTO로 변환한다")
  void getMessages() {
    Pageable pageable = PageRequest.of(0, 20);
    Page<OrderDlqMessage> page = new PageImpl<>(List.of(failedDlq()));
    when(dlqRepository.findByStatus(eq(DlqStatus.FAILED), any(Pageable.class))).thenReturn(page);

    Page<DlqMessageResponse> result = dlqService.getMessages(DlqStatus.FAILED, pageable);

    assertThat(result.getTotalElements()).isEqualTo(1);
    verify(dlqRepository).findByStatus(eq(DlqStatus.FAILED), any(Pageable.class));
  }

  @Test
  @DisplayName("countByStatus: 상태별 건수를 반환한다")
  void countByStatus() {
    when(dlqRepository.countByStatus(DlqStatus.FAILED)).thenReturn(3L);

    long count = dlqService.countByStatus(DlqStatus.FAILED);

    assertThat(count).isEqualTo(3L);
  }

  @Test
  @DisplayName("republish: 원본 토픽으로 재발행 후 RESOLVED로 전이한다")
  void republishSuccess() {
    UUID dlqId = UUID.randomUUID();
    OrderDlqMessage dlq = failedDlq();
    when(dlqRepository.findById(dlqId)).thenReturn(Optional.of(dlq));

    dlqService.republish(dlqId);

    // 원본 topic/key/payload로 재발행
    verify(orderEventProducer).republishRaw(
        eq("purchase.confirmed"), eq("key-1"), eq("{\"eventId\":\"e1\"}"));
    // 상태 RESOLVED로 전이
    assertThat(dlq.getStatus()).isEqualTo(DlqStatus.RESOLVED);
    assertThat(dlq.getRepublishCount()).isEqualTo(1);
    assertThat(dlq.getResolvedAt()).isNotNull();
  }

  @Test
  @DisplayName("republish: 존재하지 않는 DLQ ID면 예외, 재발행 안 함")
  void republishNotFound() {
    UUID dlqId = UUID.randomUUID();
    when(dlqRepository.findById(dlqId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> dlqService.republish(dlqId))
        .isInstanceOf(IllegalArgumentException.class);

    verify(orderEventProducer, never()).republishRaw(anyString(), anyString(), anyString());
  }
}