package com.omc.coupon.application.event.dto.inbound;

import java.util.UUID;

public record RefundDoneEvent(String eventId, UUID orderId, String refundReason) {}
