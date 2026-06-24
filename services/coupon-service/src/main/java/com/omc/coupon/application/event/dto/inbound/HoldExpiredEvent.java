package com.omc.coupon.application.event.dto.inbound;

import java.util.UUID;

public record HoldExpiredEvent(String eventId, UUID orderId) {}
