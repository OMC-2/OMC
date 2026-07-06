package com.omc.coupon.application.service;

import com.omc.coupon.infrastructure.util.AesTicketUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
public class CouponTicketService {

    private final byte[] aesKey;

    // 24시간 — 테스트 세션 내 재발급 없이 재사용 가능
    private static final long TTL_SECONDS = 86400;

    public CouponTicketService(
            @Value("${COUPON_TICKET_AES_KEY:Y291cG9uLXRpY2tldC1hZXMtMjU2LXNlY3JldC1rZXk=}") String aesKeyBase64
    ) {
        this.aesKey = Base64.getDecoder().decode(aesKeyBase64);
    }

    // couponId를 페이로드에서 제거 → 어떤 쿠폰에도 재사용 가능 (테스트 전용)
    public String issueTicket(UUID userId) {
        long expireAt = Instant.now().getEpochSecond() + TTL_SECONDS;
        String payload = userId + ":" + expireAt;
        try {
            return AesTicketUtil.encrypt(payload, aesKey);
        } catch (Exception e) {
            log.error("[CouponTicketService] 티켓 발급 실패. userId={}", userId, e);
            throw new RuntimeException("티켓 발급 실패", e);
        }
    }
}
