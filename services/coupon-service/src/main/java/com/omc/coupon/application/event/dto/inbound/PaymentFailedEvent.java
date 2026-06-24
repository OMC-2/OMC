package com.omc.coupon.application.event.dto.inbound;

import java.util.UUID;

public record PaymentFailedEvent(String eventId, UUID orderId) {}
