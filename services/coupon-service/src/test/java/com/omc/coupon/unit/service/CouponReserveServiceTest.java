package com.omc.coupon.unit.service;

import com.omc.common.exception.BusinessException;
import com.omc.coupon.application.service.CouponReserveService;
import com.omc.coupon.domain.entity.Coupon;
import com.omc.coupon.domain.entity.UserCoupon;
import com.omc.coupon.domain.enums.DiscountType;
import com.omc.coupon.domain.enums.UserCouponStatus;
import com.omc.coupon.domain.exception.CouponErrorCode;
import com.omc.coupon.domain.exception.UserCouponNotFoundException;
import com.omc.coupon.domain.repository.UserCouponRepository;
import com.omc.coupon.presentation.dto.request.CouponReserveRequest;
import com.omc.coupon.presentation.dto.response.CouponReserveResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CouponReserveServiceTest {

    @Mock private UserCouponRepository userCouponRepository;

    @InjectMocks private CouponReserveService couponReserveService;

    private final UUID userCouponId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    // =========================================================================
    // [시나리오 1] AVAILABLE → RESERVED 정상 선점
    // =========================================================================

    @Test
    void reserve_success() {
        // given
        CouponReserveRequest request = new CouponReserveRequest(userCouponId, orderId, userId);
        UserCoupon userCouponMock = mock(UserCoupon.class);
        Coupon couponMock = mock(Coupon.class);

        given(userCouponRepository.findById(userCouponId)).willReturn(Optional.of(userCouponMock));
        given(userCouponMock.getUserId()).willReturn(userId);
        given(userCouponMock.getCoupon()).willReturn(couponMock);
        given(couponMock.getDiscountType()).willReturn(DiscountType.AMOUNT);
        given(couponMock.getDiscountValue()).willReturn(new BigDecimal("1000"));

        // when
        CouponReserveResponse response = couponReserveService.reserve(request);

        // then
        verify(userCouponMock).reserve(orderId); // AVAILABLE → RESERVED 호출 확인
        assertThat(response).isNotNull();
    }

    // =========================================================================
    // [시나리오 2] 이미 RESERVED/USED 상태 → 선점 실패
    // =========================================================================

    @Test
    void reserve_notAvailable_throwsException() {
        // given
        CouponReserveRequest request = new CouponReserveRequest(userCouponId, orderId, userId);
        UserCoupon userCouponMock = mock(UserCoupon.class);

        given(userCouponRepository.findById(userCouponId)).willReturn(Optional.of(userCouponMock));
        given(userCouponMock.getUserId()).willReturn(userId);
        // reserve() 호출 시 UserCoupon 도메인 로직에서 예외 발생 (AVAILABLE 이 아닌 경우)
        willThrow(new BusinessException(CouponErrorCode.COUPON_NOT_AVAILABLE))
                .given(userCouponMock).reserve(orderId);

        // when & then
        assertThatThrownBy(() -> couponReserveService.reserve(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CouponErrorCode.COUPON_NOT_AVAILABLE));
    }

    // =========================================================================
    // [보조 시나리오] 존재하지 않는 userCouponId → UserCouponNotFoundException
    // =========================================================================

    @Test
    void reserve_userCouponNotFound_throwsException() {
        // given
        CouponReserveRequest request = new CouponReserveRequest(userCouponId, orderId, userId);
        given(userCouponRepository.findById(userCouponId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> couponReserveService.reserve(request))
                .isInstanceOf(UserCouponNotFoundException.class);
    }
}
