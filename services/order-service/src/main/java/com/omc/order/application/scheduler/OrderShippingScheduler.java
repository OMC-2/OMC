package com.omc.order.application.scheduler;

import com.omc.order.application.service.OrderService;
import com.omc.order.domain.entity.Order;
import com.omc.order.domain.enums.OrderStatus;
import com.omc.order.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderShippingScheduler {

  private final OrderRepository orderRepository;
  private final OrderService orderService;

  //5분마다 실행
  @Scheduled(fixedDelay = 300_000L)
  public void scheduleShipping() {
    List<Order> targets = orderRepository.findByStatus(OrderStatus.CONFIRMED);
    if (targets.isEmpty()) {
      return;
    }
    log.info("[ShippingScheduler] 배송 대상 {}건 처리 시작", targets.size());

    for (Order order : targets) {
      UUID orderId = order.getId();
      try {
        orderService.startShipping(orderId);
      } catch (Exception e) {
        log.error("[ShippingScheduler] 배송 전이 실패 - 다음 주기에 재시도: orderId={}", orderId, e);
      }
    }
  }
}
