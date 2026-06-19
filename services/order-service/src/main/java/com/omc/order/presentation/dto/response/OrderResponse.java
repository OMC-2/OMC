package com.omc.order.presentation.dto.response;

import com.omc.order.domain.entity.Order;
import com.omc.order.domain.enums.OrderStatus;
import com.omc.order.domain.enums.OrderType;

import java.time.LocalDateTime;
import java.util.UUID;

public record OrderResponse( UUID orderId,
                             UUID productId,
                             OrderType orderType,
                             Integer quantity,
                             OrderStatus status,
                             Long originalAmount,
                             Long discountAmount,
                             Long finalAmount,
                             LocalDateTime expiresAt,
                             LocalDateTime createdAt) {

  public static OrderResponse from(Order order) {
    return new OrderResponse(
        order.getOrderId(),
        order.getProductId(),
        order.getOrderType(),
        order.getQuantity(),
        order.getStatus(),
        order.getOriginalAmount(),
        order.getDiscountAmount(),
        order.getFinalAmount(),
        order.getExpiresAt(),
        order.getCreatedAt()
    );
  }
}
