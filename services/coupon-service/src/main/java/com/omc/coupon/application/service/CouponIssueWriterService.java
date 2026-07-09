package com.omc.coupon.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.common.util.UuidV7Generator;
import com.omc.coupon.application.event.dto.inbound.CouponIssueRequestedEvent;
import com.omc.coupon.domain.entity.Coupon;
import com.omc.coupon.domain.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponIssueWriterService {

    private final CouponRepository couponRepository;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    private static final String USER_COUPON_SQL =
            "INSERT INTO p_user_coupons (user_coupon_id, user_id, coupon_id, status, expired_at, created_at) VALUES (?, ?, ?, ?, ?, ?)";
    private static final String OUTBOX_SQL =
            "INSERT INTO p_coupon_outbox (event_id, aggregate_type, aggregate_id, event_type, payload, status, retry_count, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    // Redis가 실재고 원장이지만 목록 조회 응답에 표시될 DB remainingQuantity도 동기화
    private static final String REMAINING_QUANTITY_DECR_SQL =
            "UPDATE p_coupons SET remaining_quantity = GREATEST(0, remaining_quantity - ?) WHERE coupon_id = ?";

    @Transactional
    public void write(CouponIssueRequestedEvent event) {
        UUID couponId = event.couponId();
        UUID userId = event.userId();

        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalStateException("쿠폰 없음: " + couponId));

        UUID userCouponId = UuidV7Generator.generate();
        LocalDateTime now = LocalDateTime.now();

        jdbcTemplate.update(USER_COUPON_SQL, userCouponId, userId, couponId, "AVAILABLE", coupon.getExpiredAt(), now);
        // DB remaining_quantity 동기화 (Redis가 원장이지만 목록 API는 DB를 읽음)
        jdbcTemplate.update(REMAINING_QUANTITY_DECR_SQL, 1, couponId);

        UUID outboxEventId = UuidV7Generator.generate();
        String payload = toJson(Map.of(
                "eventId", outboxEventId.toString(),
                "couponId", couponId.toString(),
                "userId", userId.toString()
        ));
        jdbcTemplate.update(OUTBOX_SQL, outboxEventId, "UserCoupon", userCouponId, "COUPON_ISSUED", payload, "INIT", 0, now);
    }

    @Transactional
    public void writeBatch(List<CouponIssueRequestedEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        // 쿠폰 엔티티 조회를 위한 로컬 캐시 (중복 DB 조회 방지)
        Map<UUID, Coupon> couponCache = new HashMap<>();
        for (CouponIssueRequestedEvent event : events) {
            couponCache.computeIfAbsent(event.couponId(), id -> couponRepository.findById(id)
                    .orElseThrow(() -> new IllegalStateException("쿠폰 없음: " + id)));
        }

        List<Object[]> userCouponArgs = new ArrayList<>();
        List<Object[]> outboxArgs = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (CouponIssueRequestedEvent event : events) {
            UUID userCouponId = UuidV7Generator.generate();
            UUID couponId = event.couponId();
            UUID userId = event.userId();
            Coupon coupon = couponCache.get(couponId);

            userCouponArgs.add(new Object[]{
                    userCouponId,
                    userId,
                    couponId,
                    "AVAILABLE",
                    coupon.getExpiredAt(),
                    now
            });

            UUID outboxEventId = UuidV7Generator.generate();
            String payload = toJson(Map.of(
                    "eventId", outboxEventId.toString(),
                    "couponId", couponId.toString(),
                    "userId", userId.toString()
            ));

            outboxArgs.add(new Object[]{
                    outboxEventId,
                    "UserCoupon",
                    userCouponId,
                    "COUPON_ISSUED",
                    payload,
                    "INIT",
                    0,
                    now
            });
        }

        jdbcTemplate.batchUpdate(USER_COUPON_SQL, userCouponArgs);
        jdbcTemplate.batchUpdate(OUTBOX_SQL, outboxArgs);

        // DB remaining_quantity 동기화 — couponId별 발급 건수만큼 차감
        Map<UUID, Long> decrementMap = events.stream()
                .collect(Collectors.groupingBy(CouponIssueRequestedEvent::couponId, Collectors.counting()));
        for (Map.Entry<UUID, Long> entry : decrementMap.entrySet()) {
            jdbcTemplate.update(REMAINING_QUANTITY_DECR_SQL, entry.getValue(), entry.getKey());
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 직렬화 실패", e);
        }
    }
}
