package com.omc.order.domain.repository;

import com.omc.order.domain.entity.Order;
import com.omc.order.domain.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
  //타임아웃 롤백, 배송 처리 등에 사용할 멱등성 키 기반 조회 메서드
  Optional<Order> findByIdempotencyKey(String idempotencyKey);

  //배송 스케줄러: 확정된 주문 일괄 조회
  List<Order> findByStatus(OrderStatus status);
}
