package com.omc.coupon.unit.controller;

import com.omc.common.config.GatewaySecurityAutoConfiguration;
import com.omc.coupon.application.service.CouponReserveService;
import com.omc.coupon.domain.enums.DiscountType;
import com.omc.coupon.domain.enums.UserCouponStatus;
import com.omc.coupon.presentation.controller.CouponInternalController;
import com.omc.coupon.presentation.dto.response.UserCouponResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CouponInternalController 단위 테스트
 *
 * [범위]
 * - /internal/** 경로: SecurityConfig에서 permitAll() → 유저 토큰 없이도 접근 가능
 * - X-Gateway-Secret 헤더는 GatewayHeaderAuthFilter가 여전히 검사함
 * - CouponReserveService는 Mock으로 대체
 */
@WebMvcTest(CouponInternalController.class)
@Import(GatewaySecurityAutoConfiguration.class)
@TestPropertySource(properties = "gateway.secret=test-gateway-secret")
class CouponInternalControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private CouponReserveService couponReserveService;

    private static final String GATEWAY_SECRET = "test-gateway-secret";

    // =========================================================================
    // [시나리오 1] /internal/v1/coupons/reserve → 200 OK (유저 토큰 불필요)
    // =========================================================================

    @Test
    void reserve_internal_success() throws Exception {
        // given
        UUID userCouponId = UUID.randomUUID();
        UserCouponResponse reserveResponse = new UserCouponResponse(
                userCouponId,
                UUID.randomUUID(),
                "테스트 쿠폰",
                DiscountType.AMOUNT,
                new BigDecimal("1000"),
                null,
                UserCouponStatus.RESERVED,
                null
        );
        given(couponReserveService.reserve(any())).willReturn(reserveResponse);

        String body = String.format("""
                {
                    "userCouponId": "%s",
                    "orderId": "%s",
                    "userId": "%s"
                }
                """, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        // when & then
        mockMvc.perform(post("/internal/v1/coupons/reserve")
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userCouponId").value(userCouponId.toString()))
                .andExpect(jsonPath("$.data.status").value("RESERVED"));
    }
}
