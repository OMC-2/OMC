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

    // 티켓 유효 시간 10분 — setup() 발급 후 부하 테스트 최대 지속 시간 커버
    private static final long TTL_SECONDS = 600;

    public CouponTicketService(
            @Value("${COUPON_TICKET_AES_KEY:Y291cG9uLXRpY2tldC1hZXMtMjU2LXNlY3JldC1rZXk=}") String aesKeyBase64
    ) {
        this.aesKey = Base64.getDecoder().decode(aesKeyBase64);
    }

    public String issueTicket(UUID userId, UUID couponId) {
        long expireAt = Instant.now().getEpochSecond() + TTL_SECONDS;
        String payload = userId + ":" + couponId + ":" + expireAt;
        try {
            return AesTicketUtil.encrypt(payload, aesKey);
        } catch (Exception e) {
            log.error("[CouponTicketService] 티켓 발급 실패. userId={}, couponId={}", userId, couponId, e);
            throw new RuntimeException("티켓 발급 실패", e);
        }
    }
}
