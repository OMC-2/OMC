package com.omc.order.domain.repository;

import com.omc.order.domain.entity.OrderDlqMessage;
import com.omc.order.domain.enums.DlqStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderDlqRepository extends JpaRepository<OrderDlqMessage, UUID> {
  //관리자 화면: 상태별 목록(FAILED만 보거나 RESOLVED 이력 조회), 건수가 많을 수 있어 페이징
  Page<OrderDlqMessage> findByStatus(DlqStatus status, Pageable pageable);
  long countByStatus(DlqStatus status);
}
