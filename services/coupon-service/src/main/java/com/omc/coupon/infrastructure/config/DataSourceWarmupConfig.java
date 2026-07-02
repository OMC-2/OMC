package com.omc.coupon.infrastructure.config;

import com.omc.coupon.domain.entity.Coupon;
import com.omc.coupon.domain.entity.UserCoupon;
import com.omc.coupon.domain.enums.DiscountType;
import com.omc.coupon.domain.repository.CouponRepository;
import com.omc.coupon.domain.repository.UserCouponRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataSourceWarmupConfig {

    private static final int POOL_SIZE = 10;

    private final DataSource dataSource;
    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final PlatformTransactionManager transactionManager;
    private final EntityManager entityManager;

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        warmUpConnectionPool();
        warmUpJpaRead();
        warmUpJpaWrite();
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
                status.setRollbackOnly(); // 무조건 롤백 → DB에 안 남음
                LocalDateTime now = LocalDateTime.now();
                Coupon dummyCoupon = Coupon.create("warmup", DiscountType.AMOUNT, BigDecimal.ZERO,
                        null, 1, now, now.plusDays(1));
                entityManager.persist(dummyCoupon);
                entityManager.flush(); // INSERT coupon (롤백 예정)
                UserCoupon dummyUserCoupon = UserCoupon.create(UUID.randomUUID(), dummyCoupon, now.plusDays(1));
                entityManager.persist(dummyUserCoupon);
                entityManager.flush(); // INSERT user_coupon (롤백 예정)
                return null;
            });
        } catch (Exception e) {
            log.warn("[DataSourceWarmup] JPA write 워밍업 실패 (무시): {}", e.getMessage());
        }
        log.info("[DataSourceWarmup] JPA write 워밍업 완료");
    }
}
