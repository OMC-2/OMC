package com.omc.coupon.application.event.dto.inbound;

import java.util.UUID;

public record PaymentCompletedEvent(String eventId, UUID orderId) {}
