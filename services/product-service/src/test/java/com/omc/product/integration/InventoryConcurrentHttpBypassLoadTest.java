package com.omc.product.integration;

import com.omc.product.application.event.PaymentCompletedEvent;
import com.omc.product.application.service.InventoryService;
import com.omc.product.domain.entity.Inventory;
import com.omc.product.domain.entity.Product;
import com.omc.product.domain.enums.OutboxEventType;
import com.omc.product.domain.enums.OutboxStatus;
import com.omc.product.domain.repository.InventoryRepository;
import com.omc.product.domain.repository.OutboxEventRepository;
import com.omc.product.domain.repository.ProcessedEventRepository;
import com.omc.product.domain.repository.ProductRepository;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [부하 테스트 전용 — 기본 gradle test에는 포함되지 않음]
 *
 * 배경:
 *   k6로 Kafka에 payment.completed를 대량 발행하는 부하테스트는, 실제로는
 *   PaymentCompletedConsumer의 리스너 concurrency가 운영 설정 기준 1(미설정 =
 *   기본값)이라 컨슈머가 순차 poll로 처리, 즉 "발행 시점의 동시성"이
 *   InventoryService.confirmDeduct()의 "실행 시점 동시성"으로 이어지지 않아서,
 *   HikariCP 커넥션 풀(운영 기준 max 10)에 실제로 몰아치는 부하를 재현하지 못함
 *
 * 목적:
 *   Kafka/HTTP를 전부 우회하고, InventoryService.confirmDeduct()를 실제
 *   스레드 N개(기본 1,000)에서 동시에 직접 호출해서, HikariCP 풀 경합과
 *   낙관적 락 재시도가 실제 동시성 상황에서 어떻게 동작하는지 관찰
 *
 * 실행 방법:
 *   RUN_LOADTEST=1 ./gradlew :services:product-service:test \
 *     --tests "com.omc.product.integration.InventoryConcurrentHttpBypassLoadTest" -i
 *
 * 환경변수 (선택):
 *   LOADTEST_CONCURRENCY (기본 1000) - 동시 호출 스레드 수
 *   LOADTEST_STOCK       (기본 100)  - 테스트 상품의 초기 재고
 */
@Tag("loadtest")
class InventoryConcurrentHttpBypassLoadTest extends AbstractIntegrationTest {

    // 기본값(운영과 동일: max-concurrency=3, acquire-timeout=2000ms)을 그대로 사용
    // 격리 실험이 필요할 때만 LOADTEST_MAX_CONCURRENCY 환경변수로 임시 override
    @DynamicPropertySource
    static void semaphoreOverride(DynamicPropertyRegistry registry) {
        String override = System.getenv("LOADTEST_MAX_CONCURRENCY");
        if (override != null) {
            registry.add("inventory.deduct.max-concurrency", () -> override);
        }
    }

    @DynamicPropertySource
    static void leakDetection(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.hikari.leak-detection-threshold", () -> "3000");
        // 용의선상에 계속 등장하는 datasource-proxy(SQL 로깅/메트릭용)를 꺼서
        // 이게 원인인지 격리 테스트 — DECORATOR_EXCLUDE=true 이런 형태로 알려진 속성이 없어
        // 자동설정 클래스 자체를 제외
        registry.add("spring.autoconfigure.exclude",
                () -> "net.ttddyy.observation.boot.autoconfigure.DataSourceObservationAutoConfiguration");
    }

    @Autowired InventoryService inventoryService;
    @Autowired ProductRepository productRepository;
    @Autowired InventoryRepository inventoryRepository;
    @Autowired OutboxEventRepository outboxEventRepository;
    @Autowired ProcessedEventRepository processedEventRepository;
    @Autowired DataSource dataSource;

    private Product savedProduct;
    private int concurrency;
    private int totalStock;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        processedEventRepository.deleteAll();
        inventoryRepository.deleteAll();
        productRepository.deleteAll();

        concurrency = Integer.parseInt(System.getenv().getOrDefault("LOADTEST_CONCURRENCY", "1000"));
        totalStock = Integer.parseInt(System.getenv().getOrDefault("LOADTEST_STOCK", "100"));

        savedProduct = Product.create("부하테스트 상품 (HikariCP 검증용)", "confirmDeduct 직접 호출 부하테스트",
                100000L, "TestBrand", "TEST", null);
        productRepository.save(savedProduct);

        Inventory inventory = Inventory.create(savedProduct.getProductId(), totalStock);
        inventoryRepository.save(inventory);
    }

    @Test
    void HTTP_Kafka_우회_직접_동시_호출로_HikariCP_풀_동작_확인() throws InterruptedException {
        UUID productId = savedProduct.getProductId();

        // datasource-proxy(SQL 로깅용)가 실제 HikariDataSource를 감싸고 있을 수 있어 언랩
        DataSource realDataSource = dataSource;
        if (realDataSource instanceof ProxyDataSource proxyDataSource) {
            realDataSource = proxyDataSource.getDataSource();
        }
        HikariPoolMXBean poolMXBean = ((HikariDataSource) realDataSource).getHikariPoolMXBean();
        AtomicInteger maxActive = new AtomicInteger(0);
        AtomicInteger maxWaiting = new AtomicInteger(0);
        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
        monitor.scheduleAtFixedRate(() -> {
            maxActive.updateAndGet(prev -> Math.max(prev, poolMXBean.getActiveConnections()));
            maxWaiting.updateAndGet(prev -> Math.max(prev, poolMXBean.getThreadsAwaitingConnection()));
        }, 0, 20, TimeUnit.MILLISECONDS);

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrency);
        List<Long> durationsMs = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger exceptionCount = new AtomicInteger(0);
        AtomicReference<Throwable> firstException = new AtomicReference<>();

        // Postgres 락 대기 체인을 HikariCP 풀과 무관한 raw JDBC 커넥션으로 직접 조회 (2초 간격)
        ScheduledExecutorService lockMonitor = Executors.newSingleThreadScheduledExecutor();
        lockMonitor.scheduleAtFixedRate(() -> printPgLockDiagnostics(), 2, 2, TimeUnit.SECONDS);

        for (int i = 0; i < concurrency; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    PaymentCompletedEvent event = new PaymentCompletedEvent(
                            UUID.randomUUID().toString(),
                            UUID.randomUUID(),
                            productId,
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            100000L
                    );
                    long t0 = System.nanoTime();
                    inventoryService.confirmDeduct(event);
                    durationsMs.add((System.nanoTime() - t0) / 1_000_000);
                } catch (Exception e) {
                    exceptionCount.incrementAndGet();
                    firstException.compareAndSet(null, e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        long wallStart = System.currentTimeMillis();
        startLatch.countDown(); // 대기 중이던 스레드 전부 동시에 출발
        doneLatch.await(2, TimeUnit.MINUTES);
        long wallEnd = System.currentTimeMillis();

        executor.shutdown();
        monitor.shutdown();
        lockMonitor.shutdown();
        printPgLockDiagnostics(); // 종료 시점 마지막 스냅샷

        if (firstException.get() != null) {
            System.out.println("---- 최초 발생 예외 스택트레이스 ----");
            firstException.get().printStackTrace();
            Throwable cause = firstException.get().getCause();
            while (cause != null) {
                System.out.println("Caused by: " + cause);
                cause = cause.getCause();
            }
        }

        List<Long> sorted = new ArrayList<>(durationsMs);
        Collections.sort(sorted);
        long avg = (long) sorted.stream().mapToLong(Long::longValue).average().orElse(0);
        long p50 = percentile(sorted, 50);
        long p95 = percentile(sorted, 95);
        long p99 = percentile(sorted, 99);
        long max = sorted.isEmpty() ? 0 : sorted.get(sorted.size() - 1);

        Inventory finalInventory = inventoryRepository.findByProductId(productId).orElseThrow();
        long deducted = outboxEventRepository.findAll().stream()
                .filter(e -> e.getEventType() == OutboxEventType.STOCK_DEDUCTED
                        && e.getStatus() == OutboxStatus.PUBLISHED)
                .count();
        long failed = outboxEventRepository.findAll().stream()
                .filter(e -> e.getEventType() == OutboxEventType.STOCK_FAILED
                        && e.getStatus() == OutboxStatus.PUBLISHED)
                .count();

        System.out.printf("""

            ==================== HikariCP 동시성 부하테스트 결과 ====================
            요청 조건: 동시 스레드 %d개, 초기 재고 %d개
            전체 소요 시간: %d ms
            처리 완료: %d / %d (예외 발생: %d)

            [처리 시간 분포 (ms)]
              avg=%d  p50=%d  p95=%d  p99=%d  max=%d

            [HikariCP 풀 상태 (모니터링 최댓값)]
              최대 활성 커넥션: %d / %d (maximum-pool-size)
              최대 대기 스레드: %d

            [재고 정합성]
              STOCK_DEDUCTED: %d
              STOCK_FAILED:   %d
              합계: %d (요청 수 %d와 일치해야 함)
              최종 sold_quantity: %d (초기 재고 %d 이하여야 함)
            =======================================================================
            %n""",
                concurrency, totalStock,
                (wallEnd - wallStart),
                durationsMs.size(), concurrency, exceptionCount.get(),
                avg, p50, p95, p99, max,
                maxActive.get(), poolMXBean.getTotalConnections(),
                maxWaiting.get(),
                deducted, failed, (deducted + failed), concurrency,
                finalInventory.getSoldQuantity(), totalStock
        );

        assertThat(deducted + failed).isEqualTo((long) concurrency);
        assertThat(finalInventory.getSoldQuantity()).isEqualTo((int) deducted);
        assertThat(finalInventory.getSoldQuantity()).isLessThanOrEqualTo(totalStock);
    }

    private long percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) return 0;
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    // HikariCP 풀과 무관한 raw JDBC 커넥션으로 pg_stat_activity / pg_locks를 직접 조회
    // "누가 무엇을 기다리고 있는지"를 애플리케이션 레이어를 거치지 않고 DB에서 바로 확인
    private void printPgLockDiagnostics() {
        String url = postgres.getJdbcUrl() + "?currentSchema=product_db";
        try (Connection conn = DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {

            System.out.println("\n---- [pg 진단] " + java.time.LocalTime.now() + " ----");

            // 1) 현재 활성 세션들의 상태/대기 이벤트/실행 중인 쿼리 + 경과 시간
            try (ResultSet rs = stmt.executeQuery("""
                    SELECT pid, state, wait_event_type, wait_event,
                           EXTRACT(EPOCH FROM (now() - query_start)) AS elapsed_sec,
                           left(query, 80) AS query
                    FROM pg_stat_activity
                    WHERE datname = current_database() AND pid <> pg_backend_pid()
                    ORDER BY elapsed_sec DESC NULLS LAST
                    LIMIT 15
                    """)) {
                System.out.println("[활성 세션 상위 15개 — 경과시간 내림차순]");
                while (rs.next()) {
                    System.out.printf("  pid=%d state=%s wait=%s/%s elapsed=%.1fs query=%s%n",
                            rs.getInt("pid"), rs.getString("state"),
                            rs.getString("wait_event_type"), rs.getString("wait_event"),
                            rs.getDouble("elapsed_sec"), rs.getString("query"));
                }
            }

            // 2) 누가 누구를 막고 있는지 (표준 blocking-chain 쿼리)
            try (ResultSet rs = stmt.executeQuery("""
                    SELECT blocked_locks.pid AS blocked_pid,
                           left(blocked_activity.query, 60) AS blocked_query,
                           blocking_locks.pid AS blocking_pid,
                           left(blocking_activity.query, 60) AS blocking_query,
                           blocking_activity.state AS blocking_state
                    FROM pg_catalog.pg_locks blocked_locks
                    JOIN pg_catalog.pg_stat_activity blocked_activity ON blocked_activity.pid = blocked_locks.pid
                    JOIN pg_catalog.pg_locks blocking_locks
                        ON blocking_locks.locktype = blocked_locks.locktype
                        AND blocking_locks.database IS NOT DISTINCT FROM blocked_locks.database
                        AND blocking_locks.relation IS NOT DISTINCT FROM blocked_locks.relation
                        AND blocking_locks.page IS NOT DISTINCT FROM blocked_locks.page
                        AND blocking_locks.tuple IS NOT DISTINCT FROM blocked_locks.tuple
                        AND blocking_locks.virtualxid IS NOT DISTINCT FROM blocked_locks.virtualxid
                        AND blocking_locks.transactionid IS NOT DISTINCT FROM blocked_locks.transactionid
                        AND blocking_locks.classid IS NOT DISTINCT FROM blocked_locks.classid
                        AND blocking_locks.objid IS NOT DISTINCT FROM blocked_locks.objid
                        AND blocking_locks.objsubid IS NOT DISTINCT FROM blocked_locks.objsubid
                        AND blocking_locks.pid != blocked_locks.pid
                    JOIN pg_catalog.pg_stat_activity blocking_activity ON blocking_activity.pid = blocking_locks.pid
                    WHERE NOT blocked_locks.granted
                    """)) {
                System.out.println("[블로킹 체인 — 대기중인 락만]");
                boolean any = false;
                while (rs.next()) {
                    any = true;
                    System.out.printf("  blocked_pid=%d (%s) <- blocking_pid=%d state=%s (%s)%n",
                            rs.getInt("blocked_pid"), rs.getString("blocked_query"),
                            rs.getInt("blocking_pid"), rs.getString("blocking_state"),
                            rs.getString("blocking_query"));
                }
                if (!any) System.out.println("  (없음 — DB 레벨 락 대기는 현재 없음)");
            }

            // 3) 전체 커넥션 수 (max_connections 대비)
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT count(*) AS cnt FROM pg_stat_activity")) {
                if (rs.next()) {
                    System.out.println("[전체 커넥션 수] " + rs.getInt("cnt"));
                }
            }

        } catch (Exception e) {
            System.out.println("[pg 진단 실패] " + e);
        }
    }
}
