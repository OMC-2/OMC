package com.omc.coupon.application.warmup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.coupon.application.event.dto.inbound.CouponIssueRequestedEvent;
import com.omc.coupon.domain.entity.Coupon;
import com.omc.coupon.domain.entity.UserCoupon;
import com.omc.coupon.domain.enums.DiscountType;
import com.omc.coupon.domain.repository.CouponRepository;
import com.omc.coupon.domain.repository.UserCouponRepository;
import com.omc.coupon.infrastructure.kafka.CouponIssueProducer;
import com.omc.coupon.infrastructure.redis.CouponCacheRepository;
import com.omc.coupon.infrastructure.redis.CouponRedisRepository;
import com.omc.coupon.infrastructure.store.CouponLocalStore;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
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

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponWarmupListener {

    private static final int POOL_SIZE = 10;
    private static final int WARMUP_ISSUE_COUNT = 100;
    private static final int WARMUP_LUA_COUNT = 500;
    private static final int WARMUP_COUPON_LIMIT = 50;

    @Value("${server.port:8087}")
    private int serverPort;

    @Value("${gateway.secret:local-secret}")
    private String gatewaySecret;

    private final ApplicationContext applicationContext;
    private final DataSource dataSource;
    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final CouponRedisRepository couponRedisRepository;
    private final CouponCacheRepository couponCacheRepository;
    private final CouponLocalStore couponLocalStore;
    private final RedisTemplate<String, String> redisTemplate;
    private final PlatformTransactionManager transactionManager;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final CouponIssueProducer couponIssueProducer;

    // warmUpCouponCache()에서 캐싱한 쿠폰 ID를 이후 warmup 단계에서 재사용
    private List<UUID> activeCouponIds = new ArrayList<>();

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        AvailabilityChangeEvent.publish(applicationContext, ReadinessState.REFUSING_TRAFFIC);
        try {
            warmUpConnectionPool();
            warmUpJpaRead();
            warmUpJpaWrite();
            warmUpCouponCache();
            warmUpLettuce();
            warmUpKafkaProducer();      // Kafka producer 조기 초기화 트리거 (이후 ~20초 워밍업 동안 백그라운드 완료)
            warmUpSecurityFilterChain();
            warmUpCacheRead();          // couponCacheRepository.get() + Jackson 역직렬화 워밍업
            warmUpKafkaSerialization(); // Kafka 메시지 Jackson 직렬화 워밍업
            warmUpFullIssuePath();
            // 워밍업 중 생성된 객체를 수집해 heap을 깨끗하게 만든 뒤 real traffic 수신
            System.gc();
            log.info("[CouponWarmup] GC 완료");
            waitForJitStabilize();
        } finally {
            AvailabilityChangeEvent.publish(applicationContext, ReadinessState.ACCEPTING_TRAFFIC);
            log.info("[CouponWarmup] 워밍업 종료, 트래픽 수락 준비");
        }
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
                    .findByExpiredAtAfterAndRemainingQuantityGreaterThanOrderByCreatedAtDesc(
                            LocalDateTime.now(), 0,
                            org.springframework.data.domain.PageRequest.of(0, WARMUP_COUPON_LIMIT));
            active.forEach(coupon -> {
                couponCacheRepository.put(coupon);
                couponLocalStore.initCoupon(coupon.getCouponId().toString(), coupon.getRemainingQuantity());
            });
            activeCouponIds = active.stream().map(Coupon::getCouponId).collect(java.util.stream.Collectors.toList());
            log.info("[CouponCacheWarmup] 쿠폰 {}건 Redis 캐싱 + LocalStore 초기화 완료", active.size());
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

    // Kafka producer 첫 send()를 통해 즉시 인스턴스화 트리거.
    // idempotent producer의 ProducerId 브로커 협상(~900ms)이 이후 warmUpSecurityFilterChain() 수행 중
    // 백그라운드에서 완료되어, 테스트 시작 전 producer가 완전히 준비된 상태로 만든다.
    private void warmUpKafkaProducer() {
        try {
            couponIssueProducer.publish(
                    UUID.fromString("00000000-0000-0000-0000-000000000000"),
                    UUID.fromString("00000000-0000-0000-0000-000000000000")
            );
            log.info("[KafkaWarmup] Kafka producer 초기화 트리거 완료");
        } catch (Exception e) {
            log.warn("[KafkaWarmup] Kafka producer 초기화 트리거 실패 (무시): {}", e.getMessage());
        }
    }

    private void warmUpSecurityFilterChain() {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String base = "http://localhost:" + serverPort;

            restTemplate.getForObject(base + "/actuator/health/liveness", String.class);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Gateway-Secret", gatewaySecret);
            headers.set("X-User-Id", "00000000-0000-0000-0000-000000000001");
            headers.set("X-User-Role", "ADMIN");
            restTemplate.exchange(base + "/api/v1/coupons", HttpMethod.GET, new HttpEntity<>(headers), String.class);

            // POST 발급 경로 워밍업: Spring MVC POST 디스패처 + Security 필터 + issueCoupon() 전체 코드 경로를
            // WARMUP_ISSUE_COUNT 회 실행하여 JIT Tier 3(C1 profiling) 진입 보장.
            // 같은 userId 재사용 → 첫 1회 201, 이후 1999회 409(이미 발급) — 두 경로 모두 hot path 워밍업에 유효함.
            if (!activeCouponIds.isEmpty()) {
                UUID couponId = activeCouponIds.get(0);
                HttpHeaders issueHeaders = new HttpHeaders();
                issueHeaders.set("X-Gateway-Secret", gatewaySecret);
                issueHeaders.set("X-User-Id", "00000000-0000-0000-0000-000000000099");
                issueHeaders.set("X-User-Role", "USER");
                String issueUrl = base + "/api/v1/coupons/" + couponId + "/issue";
                HttpEntity<Void> issueEntity = new HttpEntity<>(issueHeaders);
                for (int i = 0; i < WARMUP_ISSUE_COUNT; i++) {
                    try {
                        restTemplate.exchange(issueUrl, HttpMethod.POST, issueEntity, String.class);
                    } catch (Exception e) {
                        // 201(발급 성공), 409(이미 발급), 410(재고 소진) — 모두 hot path 워밍업 목적
                    }
                }
                log.info("[SecurityWarmup] POST 발급 경로 JIT 워밍업 완료: {}회", WARMUP_ISSUE_COUNT);
            }

            log.info("[SecurityWarmup] 보안 필터체인 워밍업 완료");
        } catch (Exception e) {
            log.warn("[SecurityWarmup] 보안 필터체인 워밍업 실패 (무시): {}", e.getMessage());
        }
    }

    // couponCacheRepository.get() → Redis GET + Jackson readValue(CouponCacheDto) 를 2000회 실행
    // issueCoupon()의 findCouponDto() 경로에서 가장 빈번하게 호출되는 역직렬화 코드 경로를 JIT 컴파일
    private void warmUpCacheRead() {
        if (activeCouponIds.isEmpty()) {
            log.warn("[CacheReadWarmup] 캐싱된 쿠폰 없음 — 캐시 읽기 워밍업 생략");
            return;
        }
        try {
            int count = 0;
            int size = activeCouponIds.size();
            for (int i = 0; i < WARMUP_ISSUE_COUNT; i++) {
                couponCacheRepository.get(activeCouponIds.get(i % size));
                count++;
            }
            log.info("[CacheReadWarmup] couponCacheRepository.get() + Jackson 역직렬화 워밍업 완료: {}회", count);
        } catch (Exception e) {
            log.warn("[CacheReadWarmup] 캐시 읽기 워밍업 실패 (무시): {}", e.getMessage());
        }
    }

    // TotalCompilationTime이 500ms 간격으로 3회 연속 변화 없으면 C2 컴파일 완료로 판단
    private void waitForJitStabilize() {
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            ObjectName name = new ObjectName("java.lang:type=Compilation");
            long prev = (Long) mbs.getAttribute(name, "TotalCompilationTime");
            int stableCount = 0;
            for (int i = 0; i < 60; i++) {  // 최대 30초 대기
                Thread.sleep(500);
                long current = (Long) mbs.getAttribute(name, "TotalCompilationTime");
                if (current - prev < 10) {
                    if (++stableCount >= 3) {
                        log.info("[JitWarmup] JIT C2 컴파일 안정화 완료 (총 컴파일 시간: {}ms)", current);
                        return;
                    }
                } else {
                    stableCount = 0;
                }
                prev = current;
            }
            log.warn("[JitWarmup] JIT 안정화 대기 타임아웃 (30초)");
        } catch (Exception e) {
            log.warn("[JitWarmup] JIT 안정화 감지 실패 (무시): {}", e.getMessage());
        }
    }

    // CouponIssueRequestedEvent Jackson 직렬화를 200회 실행
    // couponIssueProducer.publish()의 writeValueAsString() 경로를 JIT 컴파일
    private void warmUpKafkaSerialization() {
        try {
            CouponIssueRequestedEvent warmupEvent =
                    new CouponIssueRequestedEvent(UUID.randomUUID(), UUID.randomUUID(), LocalDateTime.now());
            for (int i = 0; i < 200; i++) {
                objectMapper.writeValueAsString(warmupEvent);
            }
            log.info("[KafkaWarmup] Kafka 직렬화 워밍업 완료: 200회");
        } catch (Exception e) {
            log.warn("[KafkaWarmup] Kafka 직렬화 워밍업 실패 (무시): {}", e.getMessage());
        }
    }

    // tryIssueWithStockCheck() Lua 스크립트를 15000회 직접 호출해 JIT C2 컴파일 트리거
    // DB·Kafka를 거치지 않아 부작용 없음. userId는 Redis SADD 키로만 쓰이므로 랜덤 UUID로 충분
    private void warmUpFullIssuePath() {
        String warmupId = "jit-warmup-" + UUID.randomUUID();
        try {
            couponRedisRepository.initStock(warmupId, WARMUP_LUA_COUNT + 100);
            for (int i = 0; i < WARMUP_LUA_COUNT; i++) {
                couponRedisRepository.tryIssueWithStockCheck(warmupId, UUID.randomUUID().toString());
            }
            log.info("[JvmWarmup] Redis Lua 스크립트 워밍업 완료: {}회 실행", WARMUP_LUA_COUNT);
        } catch (Exception e) {
            log.warn("[JvmWarmup] Redis 발급 경로 워밍업 실패 (무시): {}", e.getMessage());
        } finally {
            redisTemplate.delete(List.of(
                    "coupon:stock:" + warmupId,
                    "coupon:issued:" + warmupId
            ));
        }
    }
}
