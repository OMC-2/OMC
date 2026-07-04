package com.omc.coupon.application.event.dto.inbound;

import java.time.LocalDateTime;
import java.util.UUID;

public record CouponIssueRequestedEvent(UUID couponId, UUID userId, LocalDateTime requestedAt) {}
