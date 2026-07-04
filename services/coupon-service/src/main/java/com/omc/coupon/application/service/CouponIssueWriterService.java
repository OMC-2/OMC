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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 직렬화 실패", e);
        }
    }
}
