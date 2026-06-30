package com.omc.raffle.infrastructure;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class TestDataLoader {

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void loadTestData() {
        log.info("Loading test data for load testing...");
        try {
            jdbcTemplate.execute(
                "INSERT INTO p_raffles (raffle_id, winner_count, created_at, ended_at, started_at, product_id, name, status) " +
                "VALUES ('123e4567-e89b-12d3-a456-426614174000', 10, NOW(), DATEADD('DAY', 7, NOW()), NOW(), '123e4567-e89b-12d3-a456-426614174002', 'Test Raffle for Load Test', 'OPEN');"
            );
            log.info("Test data loaded successfully! Raffle ID: 123e4567-e89b-12d3-a456-426614174000");
        } catch (Exception e) {
            log.error("Failed to load test data: {}", e.getMessage());
        }
    }
}
