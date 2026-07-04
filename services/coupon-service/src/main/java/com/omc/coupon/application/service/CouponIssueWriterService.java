package com.omc.coupon.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.common.util.UuidV7Generator;
import com.omc.coupon.application.event.dto.inbound.CouponIssueRequestedEvent;
import com.omc.coupon.domain.entity.Coupon;
import com.omc.coupon.domain.entity.OutboxEvent;
import com.omc.coupon.domain.entity.UserCoupon;
import com.omc.coupon.domain.enums.OutboxEventType;
import com.omc.coupon.domain.repository.CouponRepository;
import com.omc.coupon.domain.repository.OutboxEventRepository;
import com.omc.coupon.domain.repository.UserCouponRepository;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponIssueWriterService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void write(CouponIssueRequestedEvent event) {
        UUID couponId = event.couponId();
        UUID userId = event.userId();

        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalStateException("쿠폰 없음: " + couponId));

        UserCoupon userCoupon = UserCoupon.create(userId, coupon, coupon.getExpiredAt());
        userCouponRepository.save(userCoupon);

        UUID outboxEventId = UuidV7Generator.generate();
        String payload = toJson(Map.of(
                "eventId", outboxEventId.toString(),
                "couponId", couponId.toString(),
                "userId", userId.toString()
        ));
        outboxEventRepository.save(OutboxEvent.create(
                outboxEventId, "UserCoupon", userCoupon.getUserCouponId(), OutboxEventType.COUPON_ISSUED, payload
        ));

        log.info("[CouponIssueWriterService] 쿠폰 발급 저장 완료. couponId={}, userId={}, userCouponId={}",
                couponId, userId, userCoupon.getUserCouponId());
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

        String userCouponSql = "INSERT INTO p_user_coupons (user_coupon_id, user_id, coupon_id, status, expired_at, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        String outboxSql = "INSERT INTO p_coupon_outbox (event_id, aggregate_type, aggregate_id, event_type, payload, status, retry_count, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

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

        jdbcTemplate.batchUpdate(userCouponSql, userCouponArgs);
        jdbcTemplate.batchUpdate(outboxSql, outboxArgs);

        log.info("[CouponIssueWriterService] 배치 발급 저장 완료. 건수={}", events.size());
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 직렬화 실패", e);
        }
    }
}
