package com.omc.coupon.unit.controller;

import com.omc.common.config.GatewaySecurityAutoConfiguration;
import com.omc.coupon.application.service.CouponService;
import com.omc.coupon.application.service.CouponTicketService;
import com.omc.coupon.domain.enums.DiscountType;
import com.omc.coupon.presentation.controller.CouponController;
import com.omc.coupon.presentation.dto.response.CouponResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CouponController 단위 테스트
 *
 * [범위]
 * - HTTP 매핑, Bean Validation, GatewayHeaderAuthFilter, @PreAuthorize 역할 제한
 * - CouponService는 Mock으로 대체 (비즈니스 로직 검증 제외)
 *
 * [인증 방식]
 * - GatewayHeaderAuthFilter가 X-User-Id / X-User-Role 헤더로 CustomUserDetails 를 구성
 * - @WithMockUser 대신 헤더 직접 주입
 */
@WebMvcTest(CouponController.class)
@Import(GatewaySecurityAutoConfiguration.class)
@TestPropertySource(properties = "gateway.secret=test-gateway-secret")
class CouponControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private CouponService couponService;
    @MockitoBean private CouponTicketService couponTicketService;

    private static final String GATEWAY_SECRET = "test-gateway-secret";
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    // =========================================================================
    // [시나리오 1] ADMIN 권한으로 쿠폰 생성 → 201 Created
    // =========================================================================

    @Test
    void createCoupon_admin_success() throws Exception {
        // given
        CouponResponse couponResponse = new CouponResponse(
                UUID.randomUUID(), "테스트 쿠폰", DiscountType.AMOUNT,
                new BigDecimal("1000"), null, 100, 100,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(7)
        );
        given(couponService.createCoupon(any())).willReturn(couponResponse);

        String body = """
                {
                    "name": "테스트 쿠폰",
                    "discountType": "AMOUNT",
                    "discountValue": 1000,
                    "totalQuantity": 100,
                    "startedAt": "2025-01-01T00:00:00",
                    "expiredAt": "2025-12-31T23:59:59"
                }
                """;

        // when & then
        mockMvc.perform(post("/api/v1/coupons")
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", ADMIN_ID.toString())
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("테스트 쿠폰"));
    }

    // =========================================================================
    // [시나리오 2] USER 권한으로 쿠폰 생성 → 403 Forbidden (@PreAuthorize)
    // =========================================================================

    @Test
    void createCoupon_user_forbidden() throws Exception {
        String body = """
                {
                    "name": "테스트 쿠폰",
                    "discountType": "AMOUNT",
                    "discountValue": 1000,
                    "totalQuantity": 100,
                    "startedAt": "2025-01-01T00:00:00",
                    "expiredAt": "2025-12-31T23:59:59"
                }
                """;

        mockMvc.perform(post("/api/v1/coupons")
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", USER_ID.toString())
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // [시나리오 3] 필수 필드 누락 → 400 Bad Request (Bean Validation)
    // =========================================================================

    @Test
    void createCoupon_invalidRequest_returnsBadRequest() throws Exception {
        String invalidBody = """
                {
                    "discountType": "AMOUNT",
                    "discountValue": 1000,
                    "totalQuantity": 100,
                    "startedAt": "2025-01-01T00:00:00",
                    "expiredAt": "2025-12-31T23:59:59"
                }
                """;
        // name 필드 누락 → @NotBlank 실패

        mockMvc.perform(post("/api/v1/coupons")
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", ADMIN_ID.toString())
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON-001"));
    }

    // =========================================================================
    // [시나리오 4] USER 권한으로 쿠폰 발급 → 202 Accepted
    // =========================================================================

    @Test
    void issueCoupon_user_success() throws Exception {
        // given
        UUID couponId = UUID.randomUUID();
        // issueCoupon()은 void — stubbing 불필요 (Mockito default: no-op)

        // when & then
        mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", couponId)
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", USER_ID.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("쿠폰이 발급되었습니다."));
    }

    // =========================================================================
    // [시나리오 5] X-Gateway-Secret 헤더 없음 → 403 (GatewayHeaderAuthFilter)
    // =========================================================================

    @Test
    void issueCoupon_withoutGatewaySecret_returnsForbidden() throws Exception {
        UUID couponId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", couponId)
                        .header("X-User-Id", USER_ID.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // [시나리오 6] USER 권한으로 내 쿠폰 목록 조회 → 200 OK
    // =========================================================================

    @Test
    void getMyCoupons_user_success() throws Exception {
        // given
        given(couponService.getMyCoupons(any(UUID.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(Collections.emptyList()));

        // when & then
        mockMvc.perform(get("/api/v1/coupons/me")
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", USER_ID.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }
}
