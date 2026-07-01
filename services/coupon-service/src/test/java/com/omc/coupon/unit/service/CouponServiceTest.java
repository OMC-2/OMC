package com.omc.coupon.unit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.common.exception.BusinessException;
import com.omc.coupon.application.service.CouponService;
import com.omc.coupon.domain.entity.Coupon;
import com.omc.coupon.domain.entity.OutboxEvent;
import com.omc.coupon.domain.entity.UserCoupon;
import com.omc.coupon.domain.enums.DiscountType;
import com.omc.coupon.domain.enums.UserCouponStatus;
import com.omc.coupon.domain.exception.CouponErrorCode;
import com.omc.coupon.domain.exception.CouponAlreadyIssuedException;
import com.omc.coupon.domain.exception.CouponNotFoundException;
import com.omc.coupon.domain.exception.CouponOutOfStockException;
import com.omc.coupon.domain.exception.UserCouponNotFoundException;
import com.omc.coupon.domain.repository.CouponRepository;
import com.omc.coupon.domain.repository.OutboxEventRepository;
import com.omc.coupon.domain.repository.UserCouponRepository;
import com.omc.coupon.infrastructure.metrics.CouponMetrics;
import com.omc.coupon.infrastructure.redis.CouponRedisRepository;
import com.omc.coupon.presentation.dto.request.CouponCreateRequest;
import com.omc.coupon.presentation.dto.response.CouponResponse;
import com.omc.coupon.presentation.dto.response.UserCouponResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.function.Supplier;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock private CouponRepository couponRepository;
    @Mock private UserCouponRepository userCouponRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private CouponRedisRepository couponRedisRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private CouponMetrics couponMetrics;

    @InjectMocks private CouponService couponService;

    @BeforeEach
    void setUp() {
        lenient().doAnswer(inv -> ((Supplier<Long>) inv.getArgument(1)).get())
                 .when(couponMetrics).recordRedisDuration(any(), any());
    }

    private final UUID couponId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID userCouponId = UUID.randomUUID();

    // =========================================================================
    // [시나리오 1] 쿠폰 생성 성공
    // =========================================================================

    @Test
    void createCoupon_success() {
        // given
        CouponCreateRequest request = new CouponCreateRequest(
                "테스트 쿠폰", DiscountType.AMOUNT, new BigDecimal("1000"),
                null, 100,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(7)
        );
        Coupon couponMock = mock(Coupon.class);
        given(couponMock.getCouponId()).willReturn(couponId);
        given(couponMock.getTotalQuantity()).willReturn(100);
        given(couponRepository.save(any(Coupon.class))).willReturn(couponMock);

        // when
        CouponResponse response = couponService.createCoupon(request);

        // then
        assertThat(response).isNotNull();
        verify(couponRedisRepository).initStock(couponId.toString(), 100);
    }

    // =========================================================================
    // [시나리오 2] 쿠폰 발급 성공: Redis 차감 → DB 저장 → Outbox 등록 → Redis 마크
    // =========================================================================

    @Test
    void issueCoupon_success() {
        // given
        Coupon couponMock = mock(Coupon.class);
        UserCoupon userCouponMock = mock(UserCoupon.class);

        given(couponRepository.findById(couponId)).willReturn(Optional.of(couponMock));
        given(couponMock.isIssuable()).willReturn(true);
        given(couponRedisRepository.isAlreadyIssued(couponId.toString(), userId.toString())).willReturn(false);
        given(couponRedisRepository.decrementStock(couponId.toString())).willReturn(5L);
        given(userCouponRepository.findByUserIdAndCoupon_CouponId(userId, couponId)).willReturn(Optional.empty());
        given(userCouponRepository.saveAndFlush(any(UserCoupon.class))).willReturn(userCouponMock);
        given(userCouponMock.getUserCouponId()).willReturn(userCouponId);
        given(userCouponMock.getCoupon()).willReturn(couponMock); // NPE 방지
        given(userCouponMock.getStatus()).willReturn(UserCouponStatus.AVAILABLE);

        // when
        UserCouponResponse response = couponService.issueCoupon(couponId, userId);

        // then
        assertThat(response.userCouponId()).isEqualTo(userCouponId);
        assertThat(response.status()).isEqualTo(UserCouponStatus.AVAILABLE);
        verify(outboxEventRepository).save(any(OutboxEvent.class));
        verify(couponRedisRepository).markIssued(couponId.toString(), userId.toString());
    }

    // =========================================================================
    // [시나리오 3] Redis 재고 차감 시 음수 반환 → 롤백 후 예외
    // =========================================================================

    @Test
    void issueCoupon_outOfStock_throwsException() {
        // given
        Coupon couponMock = mock(Coupon.class);

        given(couponRepository.findById(couponId)).willReturn(Optional.of(couponMock));
        given(couponMock.isIssuable()).willReturn(true);
        given(couponRedisRepository.isAlreadyIssued(couponId.toString(), userId.toString())).willReturn(false);
        given(couponRedisRepository.decrementStock(couponId.toString())).willReturn(-1L); // 재고 없음

        // when & then
        assertThatThrownBy(() -> couponService.issueCoupon(couponId, userId))
                .isInstanceOf(CouponOutOfStockException.class);

        verify(couponRedisRepository).incrementStock(couponId.toString()); // Redis 롤백
        verify(userCouponRepository, never()).save(any());
    }

    // =========================================================================
    // [시나리오 4-1] Redis 발급 이력(Set) 에 이미 존재 → 예외
    // =========================================================================

    @Test
    void issueCoupon_alreadyIssuedInRedis_throwsException() {
        // given
        Coupon couponMock = mock(Coupon.class);

        given(couponRepository.findById(couponId)).willReturn(Optional.of(couponMock));
        given(couponMock.isIssuable()).willReturn(true);
        given(couponRedisRepository.isAlreadyIssued(couponId.toString(), userId.toString())).willReturn(true);

        // when & then
        assertThatThrownBy(() -> couponService.issueCoupon(couponId, userId))
                .isInstanceOf(CouponAlreadyIssuedException.class);

        verify(couponRedisRepository, never()).decrementStock(any());
    }

    // =========================================================================
    // [시나리오 4-2] Redis 차감 후 DB 중복 체크에서 발견 → Redis 롤백 + 예외
    // =========================================================================

    @Test
    void issueCoupon_alreadyIssuedInDb_rollbacksRedisAndThrows() {
        // given
        Coupon couponMock = mock(Coupon.class);
        UserCoupon existingMock = mock(UserCoupon.class);

        given(couponRepository.findById(couponId)).willReturn(Optional.of(couponMock));
        given(couponMock.isIssuable()).willReturn(true);
        given(couponRedisRepository.isAlreadyIssued(couponId.toString(), userId.toString())).willReturn(false);
        given(couponRedisRepository.decrementStock(couponId.toString())).willReturn(5L);
        given(userCouponRepository.findByUserIdAndCoupon_CouponId(userId, couponId)).willReturn(Optional.of(existingMock));

        // when & then
        assertThatThrownBy(() -> couponService.issueCoupon(couponId, userId))
                .isInstanceOf(CouponAlreadyIssuedException.class);

        verify(couponRedisRepository).incrementStock(couponId.toString()); // Redis 롤백
        verify(userCouponRepository, never()).save(any());
    }

    // =========================================================================
    // [시나리오 4-3] saveAndFlush에서 UNIQUE 위반 → Redis 재고 롤백 + 예외
    // =========================================================================

    @Test
    void issueCoupon_uniqueViolationOnFlush_rollbacksRedisAndThrows() {
        // given
        Coupon couponMock = mock(Coupon.class);

        given(couponRepository.findById(couponId)).willReturn(Optional.of(couponMock));
        given(couponMock.isIssuable()).willReturn(true);
        given(couponRedisRepository.isAlreadyIssued(couponId.toString(), userId.toString())).willReturn(false);
        given(couponRedisRepository.decrementStock(couponId.toString())).willReturn(5L);
        given(userCouponRepository.findByUserIdAndCoupon_CouponId(userId, couponId)).willReturn(Optional.empty());
        given(userCouponRepository.saveAndFlush(any(UserCoupon.class)))
                .willThrow(new DataIntegrityViolationException("UNIQUE constraint violation"));

        // when & then
        assertThatThrownBy(() -> couponService.issueCoupon(couponId, userId))
                .isInstanceOf(CouponAlreadyIssuedException.class);

        verify(couponRedisRepository).incrementStock(couponId.toString()); // Redis 재고 롤백
        verify(outboxEventRepository, never()).save(any());                 // Outbox 저장 안 됨
    }

    // =========================================================================
    // [시나리오 5-1] 쿠폰 유효기간 만료 → COUPON_EXPIRED 예외
    // =========================================================================

    @Test
    void issueCoupon_expired_throwsException() {
        // given
        Coupon couponMock = mock(Coupon.class);

        given(couponRepository.findById(couponId)).willReturn(Optional.of(couponMock));
        given(couponMock.isIssuable()).willReturn(false);
        given(couponMock.getExpiredAt()).willReturn(LocalDateTime.now().minusDays(1)); // 만료됨
        // getStartedAt() 은 expiredAt 체크가 먼저 통과하면 호출되지 않음 — 스텁 불필요

        // when & then
        assertThatThrownBy(() -> couponService.issueCoupon(couponId, userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CouponErrorCode.COUPON_EXPIRED));
    }

    // =========================================================================
    // [시나리오 5-2] 쿠폰 시작 전 → COUPON_NOT_STARTED 예외
    // =========================================================================

    @Test
    void issueCoupon_notStarted_throwsException() {
        // given
        Coupon couponMock = mock(Coupon.class);

        given(couponRepository.findById(couponId)).willReturn(Optional.of(couponMock));
        given(couponMock.isIssuable()).willReturn(false);
        given(couponMock.getExpiredAt()).willReturn(LocalDateTime.now().plusDays(7)); // 미래
        given(couponMock.getStartedAt()).willReturn(LocalDateTime.now().plusDays(1)); // 아직 시작 안 됨

        // when & then
        assertThatThrownBy(() -> couponService.issueCoupon(couponId, userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CouponErrorCode.COUPON_NOT_STARTED));
    }

    // =========================================================================
    // [시나리오 6] 내 쿠폰 목록 조회 성공
    // =========================================================================

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void getMyCoupons_success() {
        // given
        Pageable pageable = mock(Pageable.class);
        Page<UserCoupon> userCouponPage = mock(Page.class);
        Page<UserCouponResponse> responsePage = mock(Page.class);

        given(userCouponRepository.findByUserId(userId, pageable)).willReturn(userCouponPage);
        given(userCouponPage.map(any())).willReturn((Page) responsePage);

        // when
        Page<UserCouponResponse> result = couponService.getMyCoupons(userId, pageable);

        // then
        assertThat(result).isEqualTo(responsePage);
    }

    // =========================================================================
    // [시나리오 7] 내 쿠폰 상세 조회 성공 (본인 소유)
    // =========================================================================

    @Test
    void getMyCoupon_success() {
        // given
        UserCoupon userCouponMock = mock(UserCoupon.class);
        Coupon couponMock = mock(Coupon.class);

        given(userCouponRepository.findById(userCouponId)).willReturn(Optional.of(userCouponMock));
        given(userCouponMock.getUserId()).willReturn(userId);
        given(userCouponMock.getCoupon()).willReturn(couponMock);
        given(userCouponMock.getStatus()).willReturn(UserCouponStatus.AVAILABLE);

        // when
        UserCouponResponse response = couponService.getMyCoupon(userId, userCouponId);

        // then
        assertThat(response.status()).isEqualTo(UserCouponStatus.AVAILABLE);
    }

    // =========================================================================
    // [시나리오 8] 타인의 쿠폰 조회 → UserCouponNotFoundException
    // =========================================================================

    @Test
    void getMyCoupon_notOwner_throwsException() {
        // given
        UserCoupon userCouponMock = mock(UserCoupon.class);
        UUID anotherUserId = UUID.randomUUID();

        given(userCouponRepository.findById(userCouponId)).willReturn(Optional.of(userCouponMock));
        given(userCouponMock.getUserId()).willReturn(anotherUserId); // 다른 유저 소유

        // when & then
        assertThatThrownBy(() -> couponService.getMyCoupon(userId, userCouponId))
                .isInstanceOf(UserCouponNotFoundException.class);
    }
}
