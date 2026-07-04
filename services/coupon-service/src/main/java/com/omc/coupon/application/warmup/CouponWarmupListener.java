package com.omc.coupon.application.warmup;

import com.omc.coupon.application.service.CouponService;
import com.omc.coupon.domain.entity.Coupon;
import com.omc.coupon.domain.entity.UserCoupon;
import com.omc.coupon.domain.enums.DiscountType;
import com.omc.coupon.domain.repository.CouponRepository;
import com.omc.coupon.domain.repository.OutboxEventRepository;
import com.omc.coupon.domain.repository.UserCouponRepository;
import com.omc.coupon.infrastructure.redis.CouponCacheRepository;
import com.omc.coupon.infrastructure.redis.CouponRedisRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponWarmupListener {

    private static final int POOL_SIZE = 10;
    private static final int WARMUP_ISSUE_COUNT = 3;

    @Value("${server.port:8087}")
    private int serverPort;

    @Value("${gateway.secret:local-secret}")
    private String gatewaySecret;

    private final DataSource dataSource;
    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final CouponRedisRepository couponRedisRepository;
    private final CouponCacheRepository couponCacheRepository;
    private final CouponService couponService;
    private final RedisTemplate<String, String> redisTemplate;
    private final PlatformTransactionManager transactionManager;
    private final EntityManager entityManager;

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        warmUpConnectionPool();
        warmUpJpaRead();
        warmUpJpaWrite();
        warmUpCouponCache();
        warmUpLettuce();
        warmUpSecurityFilterChain();
        warmUpFullIssuePath();
    }

    private void warmUpConnectionPool() {
        int created = 0;
        for (int i = 0; i < POOL_SIZE; i++) {
            try (Connection conn = dataSource.getConnection()) {
                created++;
            } catch (SQLException e) {
                log.warn("[DataSourceWarmup] 커넥션 생성 실패 ({}/{}): {}", i + 1, POOL_SIZE, e.getMessage());
                break;
            }
        }
        log.info("[DataSourceWarmup] HikariCP 풀 워밍업 완료: {}개 커넥션 생성", created);
    }

    private void warmUpJpaRead() {
        UUID dummy = UUID.randomUUID();
        couponRepository.findById(dummy);
        userCouponRepository.findByUserIdAndCoupon_CouponId(dummy, dummy);
        log.info("[DataSourceWarmup] JPA read 워밍업 완료");
    }

    private void warmUpJpaWrite() {
        try {
            TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
            txTemplate.execute(status -> {
                status.setRollbackOnly();
                LocalDateTime now = LocalDateTime.now();
                Coupon dummyCoupon = Coupon.create("warmup", DiscountType.AMOUNT, BigDecimal.ZERO,
                        null, 1, now, now.plusDays(1));
                entityManager.persist(dummyCoupon);
                entityManager.flush();
                UserCoupon dummyUserCoupon = UserCoupon.create(UUID.randomUUID(), dummyCoupon, now.plusDays(1));
                entityManager.persist(dummyUserCoupon);
                entityManager.flush();
                return null;
            });
        } catch (Exception e) {
            log.warn("[DataSourceWarmup] JPA write 워밍업 실패 (무시): {}", e.getMessage());
        }
        log.info("[DataSourceWarmup] JPA write 워밍업 완료");
    }

    private void warmUpCouponCache() {
        try {
            List<Coupon> active = couponRepository
                    .findByExpiredAtAfterAndRemainingQuantityGreaterThan(LocalDateTime.now(), 0);
            active.forEach(couponCacheRepository::put);
            log.info("[CouponCacheWarmup] 쿠폰 {}건 Redis 캐싱 완료", active.size());
        } catch (Exception e) {
            log.warn("[CouponCacheWarmup] 쿠폰 캐싱 실패 (무시): {}", e.getMessage());
        }
    }

    private void warmUpLettuce() {
        try {
            redisTemplate.execute((RedisCallback<String>) connection -> connection.ping());
            log.info("[RedisWarmup] Lettuce 커넥션 워밍업 완료");
        } catch (Exception e) {
            log.warn("[RedisWarmup] Lettuce 워밍업 실패 (무시): {}", e.getMessage());
        }
    }

    private void warmUpSecurityFilterChain() {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String base = "http://localhost:" + serverPort;

            restTemplate.getForObject(base + "/actuator/health", String.class);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Gateway-Secret", gatewaySecret);
            headers.set("X-User-Id", "00000000-0000-0000-0000-000000000001");
            headers.set("X-User-Role", "ADMIN");
            restTemplate.exchange(base + "/api/v1/coupons", HttpMethod.GET, new HttpEntity<>(headers), String.class);

            log.info("[SecurityWarmup] 보안 필터체인 워밍업 완료");
        } catch (Exception e) {
            log.warn("[SecurityWarmup] 보안 필터체인 워밍업 실패 (무시): {}", e.getMessage());
        }
    }

    private void warmUpFullIssuePath() {
        LocalDateTime now = LocalDateTime.now();
        Coupon warmupCoupon = couponRepository.save(
                Coupon.create("__warmup__", DiscountType.AMOUNT, BigDecimal.ONE,
                        null, WARMUP_ISSUE_COUNT, now.minusMinutes(1), now.plusDays(1))
        );
        UUID couponId = warmupCoupon.getCouponId();
        couponRedisRepository.initStock(couponId.toString(), WARMUP_ISSUE_COUNT);

        int issued = 0;
        for (int i = 0; i < WARMUP_ISSUE_COUNT; i++) {
            try {
                couponService.issueCoupon(couponId, UUID.randomUUID());
                issued++;
            } catch (Exception e) {
                log.warn("[JvmWarmup] 발급 경로 워밍업 실패 ({}/{}): {}", i + 1, WARMUP_ISSUE_COUNT, e.getMessage());
            }
        }

        cleanupWarmupData(couponId);
        log.info("[JvmWarmup] 전체 발급 경로 워밍업 완료: {}회 발급 후 정리", issued);
    }

    private void cleanupWarmupData(UUID couponId) {
        try {
            TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
            txTemplate.execute(status -> {
                List<UserCoupon> warmupUserCoupons = userCouponRepository.findByCoupon_CouponId(couponId);
                if (!warmupUserCoupons.isEmpty()) {
                    List<UUID> userCouponIds = warmupUserCoupons.stream()
                            .map(UserCoupon::getUserCouponId)
                            .toList();
                    outboxEventRepository.deleteByAggregateIdIn(userCouponIds);
                }
                userCouponRepository.deleteByCoupon_CouponId(couponId);
                couponRepository.deleteById(couponId);
                return null;
            });
            redisTemplate.delete(List.of(
                    "coupon:stock:" + couponId,
                    "coupon:issued:" + couponId
            ));
            couponCacheRepository.delete(couponId);
        } catch (Exception e) {
            log.warn("[JvmWarmup] 워밍업 데이터 정리 실패 (무시): {}", e.getMessage());
        }
    }
}
