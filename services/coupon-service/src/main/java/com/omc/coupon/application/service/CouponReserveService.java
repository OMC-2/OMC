package com.omc.coupon.application.service;

import com.omc.coupon.domain.entity.UserCoupon;
import com.omc.coupon.domain.exception.UserCouponNotFoundException;
import com.omc.coupon.domain.repository.UserCouponRepository;
import com.omc.coupon.presentation.dto.request.CouponReserveRequest;
import com.omc.coupon.presentation.dto.response.CouponReserveResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponReserveService {

    private final UserCouponRepository userCouponRepository;

    /**
     * 쿠폰 선점 (AVAILABLE → RESERVED).
     * payment-service가 FeignClient로 동기 호출한다.
     * 선점 실패 시 예외를 던져 결제 자체를 중단시킨다.
     */
    @Transactional
    public CouponReserveResponse reserve(CouponReserveRequest request) {
        UserCoupon userCoupon = userCouponRepository
                .findById(request.userCouponId())
                .filter(uc -> uc.getUserId().equals(request.userId()))
                .orElseThrow(UserCouponNotFoundException::new);

        userCoupon.reserve(request.orderId()); // 상태 검증 + AVAILABLE → RESERVED

        log.info("[CouponReserveService] 쿠폰 선점 완료. userCouponId={}, orderId={}",
                request.userCouponId(), request.orderId());

        return CouponReserveResponse.from(userCoupon);
    }

    @Transactional(readOnly = true)
    public UserCoupon getUserCoupon(java.util.UUID userCouponId) {
        return userCouponRepository.findById(userCouponId).orElseThrow(UserCouponNotFoundException::new);
    }
}
