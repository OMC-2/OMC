package com.omc.coupon.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataSourceWarmupConfig {

    private static final int POOL_SIZE = 10;

    private final DataSource dataSource;

    @EventListener(ApplicationReadyEvent.class)
    public void warmUpConnectionPool() {
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
}
