package com.omc.coupon.unit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.common.exception.BusinessException;
import com.omc.coupon.application.service.CouponService;
import com.omc.coupon.application.service.CouponStockRecoveryService;
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
import com.omc.coupon.infrastructure.kafka.CouponIssueProducer;
import com.omc.coupon.infrastructure.metrics.CouponMetrics;
import com.omc.coupon.infrastructure.redis.CouponCacheDto;
import com.omc.coupon.infrastructure.redis.CouponCacheRepository;
import com.omc.coupon.infrastructure.redis.CouponRedisRepository;
import com.omc.coupon.presentation.dto.request.CouponCreateRequest;
import com.omc.coupon.presentation.dto.response.CouponResponse;
import com.omc.coupon.presentation.dto.response.UserCouponResponse;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
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
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock private CouponRepository couponRepository;
    @Mock private UserCouponRepository userCouponRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private CouponRedisRepository couponRedisRepository;
    @Mock private CouponCacheRepository couponCacheRepository;
    @Mock private CouponMetrics couponMetrics;
    @Mock private CouponStockRecoveryService couponStockRecoveryService;
    @Mock private CouponIssueProducer couponIssueProducer;
    @Mock private Tracer tracer;
    @Mock private Span mockSpan;
    @Mock private Tracer.SpanInScope mockSpanInScope;

    @InjectMocks private CouponService couponService;

    private final UUID couponId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID userCouponId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().doAnswer(inv -> ((Supplier<Long>) inv.getArgument(1)).get())
                 .when(couponMetrics).recordRedisDuration(any(), any());
        lenient().when(couponRedisRepository.hasStock(any())).thenReturn(true);

        // Tracer 체인 목업
        lenient().when(tracer.nextSpan()).thenReturn(mockSpan);
        lenient().when(mockSpan.name(any())).thenReturn(mockSpan);
        lenient().when(mockSpan.start()).thenReturn(mockSpan);
        lenient().when(tracer.withSpan(any())).thenReturn(mockSpanInScope);
    }

    private CouponCacheDto validCouponCacheDto() {
        return new CouponCacheDto(
                couponId, "테스트 쿠폰", DiscountType.AMOUNT,
                new BigDecimal("1000"), null, 100,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(7)
        );
    }

    // =========================================================================
    // [시나리오 1] 쿠폰 생성 성공
    // =========================================================================

    @Test
    void createCoupon_success() {
        CouponCreateRequest request = new CouponCreateRequest(
                "테스트 쿠폰", DiscountType.AMOUNT, new BigDecimal("1000"),
                null, 100,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(7)
        );
        Coupon couponMock = mock(Coupon.class);
        given(couponMock.getCouponId()).willReturn(couponId);
        given(couponMock.getTotalQuantity()).willReturn(100);
        given(couponRepository.save(any(Coupon.class))).willReturn(couponMock);

        CouponResponse response = couponService.createCoupon(request);

        assertThat(response).isNotNull();
        verify(couponRedisRepository).initStock(couponId.toString(), 100);
    }

    // =========================================================================
    // [시나리오 2] 쿠폰 발급 성공: Lua 원자 처리 → Kafka 발행
    // =========================================================================

    @Test
    void issueCoupon_success() {
        given(couponCacheRepository.get(couponId)).willReturn(Optional.of(validCouponCacheDto()));
        given(couponRedisRepository.tryIssueWithStockCheck(couponId.toString(), userId.toString())).willReturn(5L);

        couponService.issueCoupon(couponId, userId);

        verify(couponIssueProducer).publish(couponId, userId);
    }

    // =========================================================================
    // [시나리오 3] Lua 스크립트 재고 소진 반환(-1) → 예외 (롤백은 Lua 내부)
    // =========================================================================

    @Test
    void issueCoupon_outOfStock_throwsException() {
        given(couponCacheRepository.get(couponId)).willReturn(Optional.of(validCouponCacheDto()));
        given(couponRedisRepository.tryIssueWithStockCheck(couponId.toString(), userId.toString())).willReturn(-1L);

        assertThatThrownBy(() -> couponService.issueCoupon(couponId, userId))
                .isInstanceOf(CouponOutOfStockException.class);

        verify(couponIssueProducer, never()).publish(any(), any());
    }

    // =========================================================================
    // [시나리오 4] Lua 스크립트 이미 발급 반환(-2) → 예외
    // =========================================================================

    @Test
    void issueCoupon_alreadyIssuedInRedis_throwsException() {
        given(couponCacheRepository.get(couponId)).willReturn(Optional.of(validCouponCacheDto()));
        given(couponRedisRepository.tryIssueWithStockCheck(couponId.toString(), userId.toString())).willReturn(-2L);
        assertThatThrownBy(() -> couponService.issueCoupon(couponId, userId))
                .isInstanceOf(CouponAlreadyIssuedException.class);

        verify(couponIssueProducer, never()).publish(any(), any());
    }

    // =========================================================================
    // [시나리오 5-1] 쿠폰 유효기간 만료 → COUPON_EXPIRED 예외
    // =========================================================================

    @Test
    void issueCoupon_expired_throwsException() {
        CouponCacheDto expiredDto = new CouponCacheDto(
                couponId, "만료 쿠폰", DiscountType.AMOUNT,
                new BigDecimal("1000"), null, 100,
                LocalDateTime.now().minusDays(10), LocalDateTime.now().minusDays(1)
        );
        given(couponCacheRepository.get(couponId)).willReturn(Optional.of(expiredDto));

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
        CouponCacheDto notStartedDto = new CouponCacheDto(
                couponId, "시작 전 쿠폰", DiscountType.AMOUNT,
                new BigDecimal("1000"), null, 100,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(30)
        );
        given(couponCacheRepository.get(couponId)).willReturn(Optional.of(notStartedDto));


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
        Pageable pageable = mock(Pageable.class);
        Page<UserCoupon> userCouponPage = mock(Page.class);
        Page<UserCouponResponse> responsePage = mock(Page.class);

        given(userCouponRepository.findByUserId(userId, pageable)).willReturn(userCouponPage);
        given(userCouponPage.map(any())).willReturn((Page) responsePage);

        Page<UserCouponResponse> result = couponService.getMyCoupons(userId, pageable);

        assertThat(result).isEqualTo(responsePage);
    }

    // =========================================================================
    // [시나리오 7] 내 쿠폰 상세 조회 성공 (본인 소유)
    // =========================================================================

    @Test
    void getMyCoupon_success() {
        UserCoupon userCouponMock = mock(UserCoupon.class);
        Coupon couponMock = mock(Coupon.class);

        given(userCouponRepository.findById(userCouponId)).willReturn(Optional.of(userCouponMock));
        given(userCouponMock.getUserId()).willReturn(userId);
        given(userCouponMock.getCoupon()).willReturn(couponMock);
        given(userCouponMock.getStatus()).willReturn(UserCouponStatus.AVAILABLE);

        UserCouponResponse response = couponService.getMyCoupon(userId, userCouponId);

        assertThat(response.status()).isEqualTo(UserCouponStatus.AVAILABLE);
    }

    // =========================================================================
    // [시나리오 8] 타인의 쿠폰 조회 → UserCouponNotFoundException
    // =========================================================================

    @Test
    void getMyCoupon_notOwner_throwsException() {
        UserCoupon userCouponMock = mock(UserCoupon.class);
        UUID anotherUserId = UUID.randomUUID();

        given(userCouponRepository.findById(userCouponId)).willReturn(Optional.of(userCouponMock));
        given(userCouponMock.getUserId()).willReturn(anotherUserId);

        assertThatThrownBy(() -> couponService.getMyCoupon(userId, userCouponId))
                .isInstanceOf(UserCouponNotFoundException.class);
    }

    // =========================================================================
    // [시나리오 9] Redis key 없을 때 → syncCouponStock 호출 후 정상 발급
    // =========================================================================

    @Test
    void issueCoupon_redisKeyMissing_syncsThenIssues() {
        given(couponCacheRepository.get(couponId)).willReturn(Optional.of(validCouponCacheDto()));
        given(couponRedisRepository.tryIssueWithStockCheck(couponId.toString(), userId.toString())).willReturn(-3L);
        given(couponRedisRepository.tryIssue(couponId.toString(), userId.toString())).willReturn(69L);

        couponService.issueCoupon(couponId, userId);

        verify(couponStockRecoveryService).syncCouponStock(couponId);
        verify(couponIssueProducer).publish(couponId, userId);
    }

    // =========================================================================
    // [시나리오 10] Redis key 있을 때 → syncCouponStock 호출하지 않음
    // =========================================================================

    @Test
    void issueCoupon_redisKeyExists_skipsSync() {
        given(couponCacheRepository.get(couponId)).willReturn(Optional.of(validCouponCacheDto()));
        given(couponRedisRepository.tryIssueWithStockCheck(couponId.toString(), userId.toString())).willReturn(5L);

        couponService.issueCoupon(couponId, userId);

        verify(couponStockRecoveryService, never()).syncCouponStock(any());
    }

    // =========================================================================
    // [시나리오 11] 쿠폰 삭제 성공 (소프트 딜리트)
    // =========================================================================

    @Test
    void deleteCoupon_success() {
        // given
        Coupon couponMock = mock(Coupon.class);
        given(couponRepository.findByCouponIdAndDeletedAtIsNull(couponId)).willReturn(Optional.of(couponMock));

        // when
        couponService.deleteCoupon(couponId);

        // then
        verify(couponMock).softDelete(nullable(UUID.class));
        verify(couponRedisRepository).deleteStock(couponId.toString());
    }

    // =========================================================================
    // [시나리오 12] 존재하지 않는 쿠폰 삭제 → CouponNotFoundException
    // =========================================================================

    @Test
    void deleteCoupon_notFound_throwsException() {
        // given
        given(couponRepository.findByCouponIdAndDeletedAtIsNull(couponId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> couponService.deleteCoupon(couponId))
                .isInstanceOf(CouponNotFoundException.class);

        verify(couponRedisRepository, never()).deleteStock(any());
    }

}
