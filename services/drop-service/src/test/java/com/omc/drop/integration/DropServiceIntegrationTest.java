package com.omc.drop.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.drop.application.scheduler.HoldExpireScheduler;
import com.omc.drop.domain.entity.Drop;
import com.omc.drop.domain.repository.DropProcessedEventRepository;
import com.omc.drop.domain.repository.DropRepository;
import com.omc.drop.application.event.consumer.PaymentCompletedEvent;
import com.omc.drop.application.event.consumer.PaymentFailedEvent;
import com.omc.drop.application.event.consumer.StockFailedEvent;
import com.omc.drop.infrastructure.redis.PurchaseRedisRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * drop-service 전체 통합 테스트.
 * - PostgreSQL TestContainer: Drop 영속성 검증
 * - Redis TestContainer: 구매 선점 로직 검증
 * - EmbeddedKafka: 이벤트 발행 검증
 * - Spring Context는 1번만 생성하고 @Nested 클래스로 기능별 분리
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@EmbeddedKafka(
        partitions = 1,
        bootstrapServersProperty = "spring.kafka.bootstrap-servers",
        topics = {
                "purchase.confirmed", "drop.opened", "drop.closed",
                "hold.expired", "refund.requested",
                "payment.completed", "payment.failed", "stock.failed"
        }
)
@DisplayName("Drop Service 통합 테스트")
class DropServiceIntegrationTest {

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @SuppressWarnings("resource")
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7.2-alpine")
            .withExposedPorts(6379);

    static {
        postgres.start();
        redis.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired DropRepository dropRepository;
    @Autowired PurchaseRedisRepository purchaseRedisRepository;
    @Autowired DropProcessedEventRepository processedEventRepository;
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired EmbeddedKafkaBroker embeddedKafkaBroker;
    @Autowired HoldExpireScheduler holdExpireScheduler;

    private static final String GW_SECRET  = "test-secret";
    private static final String ADMIN_ID   = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String USER_ID    = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
    private static final String PRODUCT_ID = "cccccccc-cccc-cccc-cccc-cccccccccccc";

    // =========================================================================
    // Admin API — POST/PUT/DELETE /api/v1/admin/drops
    // =========================================================================

    @Nested
    @DisplayName("Admin API 테스트")
    class AdminTests {

        @BeforeEach
        void setUp() {
            dropRepository.deleteAll();
        }

        @Test
        @DisplayName("ADMIN 역할로 드롭을 생성하면 201을 반환하고 DB에 저장된다")
        void create_adminRole_returns201_and_savedToDb() throws Exception {
            String body = """
                    {
                        "productId": "%s",
                        "startAt": "%s",
                        "endAt": "%s",
                        "totalQty": 50,
                        "holdTtlSec": 300
                    }
                    """.formatted(PRODUCT_ID, future(1), future(2));

            mockMvc.perform(post("/api/v1/admin/drops")
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", ADMIN_ID)
                            .header("X-User-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.dropId").isNotEmpty())
                    .andExpect(jsonPath("$.data.productId").value(PRODUCT_ID))
                    .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                    .andExpect(jsonPath("$.data.totalQty").value(50))
                    .andExpect(jsonPath("$.data.holdTtlSec").value(300));

            assertThat(dropRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("USER 역할로 드롭 생성 시 403을 반환한다")
        void create_userRole_returns403() throws Exception {
            String body = """
                    {
                        "productId": "%s",
                        "startAt": "%s",
                        "endAt": "%s",
                        "totalQty": 50,
                        "holdTtlSec": 300
                    }
                    """.formatted(PRODUCT_ID, future(1), future(2));

            mockMvc.perform(post("/api/v1/admin/drops")
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Role", "USER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden());

            assertThat(dropRepository.count()).isZero();
        }

        @Test
        @DisplayName("productId 누락 시 400을 반환한다")
        void create_missingProductId_returns400() throws Exception {
            String body = """
                    {
                        "startAt": "%s",
                        "endAt": "%s",
                        "totalQty": 50,
                        "holdTtlSec": 300
                    }
                    """.formatted(future(1), future(2));

            mockMvc.perform(post("/api/v1/admin/drops")
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", ADMIN_ID)
                            .header("X-User-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("COMMON-001"));
        }

        @Test
        @DisplayName("SCHEDULED 드롭 수정 시 200을 반환하고 DB에 반영된다")
        void update_scheduledDrop_returns200_and_updatedInDb() throws Exception {
            Drop saved = dropRepository.save(scheduledDrop());

            String body = """
                    {
                        "startAt": "%s",
                        "endAt": "%s",
                        "totalQty": 200,
                        "holdTtlSec": 600
                    }
                    """.formatted(future(1), future(3));

            mockMvc.perform(put("/api/v1/admin/drops/{dropId}", saved.getDropId())
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", ADMIN_ID)
                            .header("X-User-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalQty").value(200))
                    .andExpect(jsonPath("$.data.holdTtlSec").value(600));

            Drop updated = dropRepository.findById(saved.getDropId()).orElseThrow();
            assertThat(updated.getTotalQty()).isEqualTo(200);
        }

        @Test
        @DisplayName("존재하지 않는 dropId 수정 시 404와 DROP-001을 반환한다")
        void update_notFound_returns404() throws Exception {
            String body = """
                    {
                        "startAt": "%s",
                        "endAt": "%s",
                        "totalQty": 100,
                        "holdTtlSec": 300
                    }
                    """.formatted(future(1), future(2));

            mockMvc.perform(put("/api/v1/admin/drops/{dropId}", UUID.randomUUID())
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", ADMIN_ID)
                            .header("X-User-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("DROP-001"));
        }

        @Test
        @DisplayName("OPEN 상태 드롭 수정 시 400과 DROP-003을 반환한다")
        void update_openDrop_returns400() throws Exception {
            Drop saved = dropRepository.save(openDrop());

            String body = """
                    {
                        "startAt": "%s",
                        "endAt": "%s",
                        "totalQty": 100,
                        "holdTtlSec": 300
                    }
                    """.formatted(future(1), future(2));

            mockMvc.perform(put("/api/v1/admin/drops/{dropId}", saved.getDropId())
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", ADMIN_ID)
                            .header("X-User-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("DROP-003"));
        }

        @Test
        @DisplayName("SCHEDULED 드롭 삭제 시 204를 반환하고 소프트딜리트된다")
        void delete_scheduledDrop_returns204_and_softDeleted() throws Exception {
            Drop saved = dropRepository.save(scheduledDrop());

            mockMvc.perform(delete("/api/v1/admin/drops/{dropId}", saved.getDropId())
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", ADMIN_ID)
                            .header("X-User-Role", "ADMIN"))
                    .andExpect(status().isNoContent());

            // @SQLRestriction("deleted_at IS NULL") 로 인해 삭제된 드롭은 조회 안 됨
            assertThat(dropRepository.findById(saved.getDropId())).isEmpty();
        }

        @Test
        @DisplayName("OPEN 상태 드롭 삭제 시 400과 DROP-003을 반환한다")
        void delete_openDrop_returns400() throws Exception {
            Drop saved = dropRepository.save(openDrop());

            mockMvc.perform(delete("/api/v1/admin/drops/{dropId}", saved.getDropId())
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", ADMIN_ID)
                            .header("X-User-Role", "ADMIN"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("DROP-003"));
        }

        @Test
        @DisplayName("존재하지 않는 dropId 삭제 시 404와 DROP-001을 반환한다")
        void delete_notFound_returns404() throws Exception {
            mockMvc.perform(delete("/api/v1/admin/drops/{dropId}", UUID.randomUUID())
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", ADMIN_ID)
                            .header("X-User-Role", "ADMIN"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("DROP-001"));
        }
    }

    // =========================================================================
    // 조회 API — GET /api/v1/drops
    // =========================================================================

    @Nested
    @DisplayName("조회 API 테스트")
    class QueryTests {

        @BeforeEach
        void setUp() {
            dropRepository.deleteAll();
        }

        @Test
        @DisplayName("드롭이 없으면 빈 목록을 반환한다")
        void listDrops_empty_returns200() throws Exception {
            mockMvc.perform(get("/api/v1/drops")
                            .header("X-Gateway-Secret", GW_SECRET))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isEmpty())
                    .andExpect(jsonPath("$.data.totalElements").value(0));
        }

        @Test
        @DisplayName("드롭 목록을 페이지로 반환한다")
        void listDrops_multipleDrops_returnsPaged() throws Exception {
            dropRepository.save(scheduledDrop());
            dropRepository.save(scheduledDrop());
            dropRepository.save(openDrop());

            mockMvc.perform(get("/api/v1/drops")
                            .header("X-Gateway-Secret", GW_SECRET)
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(3))
                    .andExpect(jsonPath("$.data.content.length()").value(3));
        }

        @Test
        @DisplayName("status 파라미터로 드롭을 필터링한다")
        void listDrops_filterByStatus_returnsOnlyMatching() throws Exception {
            dropRepository.save(scheduledDrop());
            dropRepository.save(scheduledDrop());
            dropRepository.save(openDrop());

            mockMvc.perform(get("/api/v1/drops")
                            .header("X-Gateway-Secret", GW_SECRET)
                            .param("status", "OPEN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.data.content[0].status").value("OPEN"));
        }

        @Test
        @DisplayName("dropId로 드롭 단건을 조회한다")
        void getById_exists_returns200() throws Exception {
            Drop saved = dropRepository.save(scheduledDrop());

            mockMvc.perform(get("/api/v1/drops/{dropId}", saved.getDropId())
                            .header("X-Gateway-Secret", GW_SECRET))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.dropId").value(saved.getDropId().toString()))
                    .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                    .andExpect(jsonPath("$.data.totalQty").value(100));
        }

        @Test
        @DisplayName("존재하지 않는 dropId 조회 시 404와 DROP-001을 반환한다")
        void getById_notFound_returns404() throws Exception {
            mockMvc.perform(get("/api/v1/drops/{dropId}", UUID.randomUUID())
                            .header("X-Gateway-Secret", GW_SECRET))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("DROP-001"));
        }
    }

    // =========================================================================
    // 구매 선점 API — POST /api/v1/drops/{dropId}/purchase
    // =========================================================================

    @Nested
    @DisplayName("구매 선점 API 테스트")
    class PurchaseTests {

        private UUID dropId;

        @BeforeEach
        void setUp() {
            // 매 테스트마다 고유한 dropId → Redis 키 충돌 없음
            dropId = UUID.randomUUID();
        }

        @AfterEach
        void tearDown() {
            // warmup이 만든 Redis 키 정리 (컨테이너 공유 기간 동안 누적 방지)
            purchaseRedisRepository.deleteDropKeys(dropId);
        }

        @Test
        @DisplayName("OPEN 드롭에 구매 선점 시 202와 orderId, queueNumber를 반환하고 Redis 상태가 변경된다")
        void purchase_openDrop_returns202_and_redisStateUpdated() throws Exception {
            purchaseRedisRepository.warmup(dropId, 100, 300, UUID.fromString(PRODUCT_ID));

            MvcResult result = mockMvc.perform(post("/api/v1/drops/{dropId}/purchase", dropId)
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.data.orderId").isNotEmpty())
                    .andExpect(jsonPath("$.data.queueNumber").value(1))
                    .andReturn();

            UUID orderId = UUID.fromString(
                    objectMapper.readTree(result.getResponse().getContentAsString())
                            .at("/data/orderId").asText()
            );

            assertThat(purchaseRedisRepository.getStock(dropId)).isEqualTo(99);
            assertThat(purchaseRedisRepository.hasPurchased(dropId, UUID.fromString(USER_ID))).isTrue();
            assertThat(purchaseRedisRepository.hasHold(dropId, orderId)).isTrue();
        }

        @Test
        @DisplayName("같은 사용자가 중복 구매 시 409와 DROP-005를 반환한다")
        void purchase_duplicate_returns409() throws Exception {
            purchaseRedisRepository.warmup(dropId, 100, 300, UUID.fromString(PRODUCT_ID));

            // 첫 번째 구매
            mockMvc.perform(post("/api/v1/drops/{dropId}/purchase", dropId)
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isAccepted());

            // 중복 구매
            mockMvc.perform(post("/api/v1/drops/{dropId}/purchase", dropId)
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("DROP-005"));
        }

        @Test
        @DisplayName("재고 소진 시 409와 DROP-004를 반환한다")
        void purchase_soldOut_returns409() throws Exception {
            purchaseRedisRepository.warmup(dropId, 1, 300, UUID.fromString(PRODUCT_ID));

            String otherUserId = UUID.randomUUID().toString();

            // 첫 번째 사용자 구매 (재고 1개 소진)
            mockMvc.perform(post("/api/v1/drops/{dropId}/purchase", dropId)
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", otherUserId)
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isAccepted());

            // 재고 소진 후 구매 시도
            mockMvc.perform(post("/api/v1/drops/{dropId}/purchase", dropId)
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("DROP-004"));
        }

        @Test
        @DisplayName("OPEN 상태가 아닌 드롭 구매 시 409와 DROP-002를 반환한다")
        void purchase_notOpenDrop_returns409() throws Exception {
            // Redis warmup 없이 호출 → isOpen() == false
            mockMvc.perform(post("/api/v1/drops/{dropId}/purchase", dropId)
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("DROP-002"));
        }

        @Test
        @DisplayName("인증 없이 구매 시 403을 반환한다")
        void purchase_noAuth_returns403() throws Exception {
            purchaseRedisRepository.warmup(dropId, 100, 300, UUID.fromString(PRODUCT_ID));

            // X-Gateway-Secret 없이 호출
            mockMvc.perform(post("/api/v1/drops/{dropId}/purchase", dropId))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("warmup을 두 번 호출해도 재고가 초기화되지 않는다 (Lua 멱등성)")
        void warmup_calledTwice_doesNotResetStock() throws Exception {
            // 첫 번째 warmup
            purchaseRedisRepository.warmup(dropId, 100, 300, UUID.fromString(PRODUCT_ID));

            // 구매 1건으로 재고 감소
            mockMvc.perform(post("/api/v1/drops/{dropId}/purchase", dropId)
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isAccepted());

            assertThat(purchaseRedisRepository.getStock(dropId)).isEqualTo(99);

            // 두 번째 warmup — status 키가 이미 존재하므로 Lua가 0 반환하고 값을 건드리지 않는다
            purchaseRedisRepository.warmup(dropId, 100, 300, UUID.fromString(PRODUCT_ID));

            assertThat(purchaseRedisRepository.getStock(dropId)).isEqualTo(99);  // 100으로 리셋되면 안 됨
        }

        @Test
        @DisplayName("구매 선점 성공 시 Stream에 이벤트가 기록된다")
        void purchase_openDrop_writesEventToStream() throws Exception {
            purchaseRedisRepository.warmup(dropId, 100, 300, UUID.fromString(PRODUCT_ID));

            long beforeSize = purchaseRedisRepository.getStreamSize() != null
                    ? purchaseRedisRepository.getStreamSize() : 0L;

            mockMvc.perform(post("/api/v1/drops/{dropId}/purchase", dropId)
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isAccepted());

            // XADD는 XACK 이후에도 스트림 본체에 남음 → 즉시 검증 가능
            assertThat(purchaseRedisRepository.getStreamSize()).isGreaterThan(beforeSize);
        }
    }

    // =========================================================================
    // Kafka Consumer — payment.completed / payment.failed / stock.failed
    // =========================================================================

    @Nested
    @DisplayName("Kafka Consumer 테스트")
    class ConsumerTests {

        // JsonSerializer는 String을 이중 인코딩 → StringSerializer로 직접 구성
        private KafkaTemplate<String, String> stringKafkaTemplate;

        private UUID dropId;
        private UUID userId;
        private UUID orderId;

        @BeforeEach
        void setUp() throws Exception {
            var props = KafkaTestUtils.producerProps(embeddedKafkaBroker);
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            stringKafkaTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));

            dropId = UUID.randomUUID();
            userId = UUID.randomUUID();

            // Redis warmup 후 실제 구매 선점으로 hold 생성
            purchaseRedisRepository.warmup(dropId, 100, 300, UUID.fromString(PRODUCT_ID));

            MvcResult result = mockMvc.perform(post("/api/v1/drops/{dropId}/purchase", dropId)
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", userId.toString())
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isAccepted())
                    .andReturn();

            orderId = UUID.fromString(
                    objectMapper.readTree(result.getResponse().getContentAsString())
                            .at("/data/orderId").asText()
            );
        }

        @AfterEach
        void tearDown() {
            purchaseRedisRepository.deleteDropKeys(dropId);
            processedEventRepository.deleteAll();
        }

        @Test
        @DisplayName("payment.completed(INSTANT) 수신 시 hold가 제거되고 재고는 유지된다")
        void onPaymentCompleted_instant_removesHold() throws Exception {
            String eventId = UUID.randomUUID().toString();
            PaymentCompletedEvent event = new PaymentCompletedEvent(
                    eventId, "INSTANT", userId, null,
                    10000L, 0L, 10000L, orderId, dropId
            );

            stringKafkaTemplate.send("payment.completed", objectMapper.writeValueAsString(event));

            // hold 제거 대기
            await().atMost(5, TimeUnit.SECONDS)
                    .until(() -> !purchaseRedisRepository.hasHold(dropId, orderId));

            // confirmHold는 hold만 제거 — 재고·구매자는 그대로
            assertThat(purchaseRedisRepository.hasHold(dropId, orderId)).isFalse();
            assertThat(purchaseRedisRepository.getStock(dropId)).isEqualTo(99);
            assertThat(purchaseRedisRepository.hasPurchased(dropId, userId)).isTrue();
            assertThat(processedEventRepository.existsById(eventId)).isTrue();
        }

        @Test
        @DisplayName("payment.completed(RAFFLE) 수신 시 처리를 스킵하고 hold가 유지된다")
        void onPaymentCompleted_raffle_skipsProcessing() throws Exception {
            String eventId = UUID.randomUUID().toString();
            PaymentCompletedEvent event = new PaymentCompletedEvent(
                    eventId, "RAFFLE", userId, null,
                    10000L, 0L, 10000L, orderId, dropId
            );

            stringKafkaTemplate.send("payment.completed", objectMapper.writeValueAsString(event));

            // 컨슈머가 메시지를 수신했을 충분한 시간 대기
            Thread.sleep(2000);

            // RAFFLE은 스킵 → hold 유지, processedEvent 미저장
            assertThat(purchaseRedisRepository.hasHold(dropId, orderId)).isTrue();
            assertThat(processedEventRepository.existsById(eventId)).isFalse();
        }

        @Test
        @DisplayName("payment.failed(INSTANT) 수신 시 재고가 복구되고 구매자가 취소된다")
        void onPaymentFailed_instant_recoversStock() throws Exception {
            String eventId = UUID.randomUUID().toString();
            PaymentFailedEvent event = new PaymentFailedEvent(
                    eventId, "INSTANT", userId, "PAYMENT_TIMEOUT", orderId, dropId
            );

            stringKafkaTemplate.send("payment.failed", objectMapper.writeValueAsString(event));

            // 재고 복구 대기
            await().atMost(5, TimeUnit.SECONDS)
                    .until(() -> purchaseRedisRepository.getStock(dropId) == 100);

            // recoverHold: 재고 복구 + 구매자 취소 + hold 제거
            assertThat(purchaseRedisRepository.getStock(dropId)).isEqualTo(100);
            assertThat(purchaseRedisRepository.hasPurchased(dropId, userId)).isFalse();
            assertThat(purchaseRedisRepository.hasHold(dropId, orderId)).isFalse();
            assertThat(processedEventRepository.existsById(eventId)).isTrue();
        }

        @Test
        @DisplayName("stock.failed 수신 시 재고가 복구되고 구매자가 취소된다")
        void onStockFailed_recoversStock() throws Exception {
            String eventId = UUID.randomUUID().toString();
            StockFailedEvent event = new StockFailedEvent(
                    eventId, orderId, UUID.fromString(PRODUCT_ID), dropId, userId
            );

            stringKafkaTemplate.send("stock.failed", objectMapper.writeValueAsString(event));

            await().atMost(5, TimeUnit.SECONDS)
                    .until(() -> purchaseRedisRepository.getStock(dropId) == 100);

            assertThat(purchaseRedisRepository.getStock(dropId)).isEqualTo(100);
            assertThat(purchaseRedisRepository.hasPurchased(dropId, userId)).isFalse();
            assertThat(purchaseRedisRepository.hasHold(dropId, orderId)).isFalse();
            assertThat(processedEventRepository.existsById(eventId)).isTrue();
        }

        @Test
        @DisplayName("동일 eventId 수신 시 두 번째 이벤트를 스킵한다")
        void duplicateEvent_skipsSecondProcessing() throws Exception {
            String eventId = UUID.randomUUID().toString();
            PaymentFailedEvent event = new PaymentFailedEvent(
                    eventId, "INSTANT", userId, "PAYMENT_TIMEOUT", orderId, dropId
            );
            String message = objectMapper.writeValueAsString(event);

            // 첫 번째 전송 — 재고 복구됨
            stringKafkaTemplate.send("payment.failed", message);
            await().atMost(5, TimeUnit.SECONDS)
                    .until(() -> processedEventRepository.existsById(eventId));

            // 두 번째 전송 — 동일 eventId → DB 중복 체크로 스킵
            stringKafkaTemplate.send("payment.failed", message);
            Thread.sleep(2000);

            // 두 번 복구되지 않음 — processedEvent는 1건만 존재
            assertThat(processedEventRepository.findAll())
                    .filteredOn(e -> e.getEventId().equals(eventId))
                    .hasSize(1);
        }
    }

    // =========================================================================
    // HoldExpireScheduler — hold TTL 만료 처리
    // =========================================================================

    @Nested
    @DisplayName("HoldExpireScheduler 테스트")
    class HoldExpireSchedulerTests {

        private UUID dropId;
        private UUID userId;
        private UUID orderId;

        @BeforeEach
        void setUp() throws Exception {
            userId = UUID.randomUUID();

            // OPEN 드롭 DB 저장 — 스케줄러가 findByStatus(OPEN)로 조회함
            Drop savedDrop = dropRepository.save(openDrop());
            dropId = savedDrop.getDropId();

            // holdTtlSec=1 — 1초 후 만료되는 hold 생성
            purchaseRedisRepository.warmup(dropId, 100, 1, UUID.fromString(PRODUCT_ID));

            MvcResult result = mockMvc.perform(post("/api/v1/drops/{dropId}/purchase", dropId)
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", userId.toString())
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isAccepted())
                    .andReturn();

            orderId = UUID.fromString(
                    objectMapper.readTree(result.getResponse().getContentAsString())
                            .at("/data/orderId").asText()
            );
        }

        @AfterEach
        void tearDown() {
            dropRepository.deleteAll();
            purchaseRedisRepository.deleteDropKeys(dropId);
        }

        @Test
        @DisplayName("만료된 hold는 재고를 복구하고 hold를 제거한다")
        void expireHolds_expiredHold_recoversStockAndRemovesHold() throws Exception {
            // holdTtlSec=1 경과 대기
            Thread.sleep(2000);

            holdExpireScheduler.expireHolds();

            // expire.lua: ZREM holds + INCR stock (purchased Set은 제거하지 않음)
            assertThat(purchaseRedisRepository.getStock(dropId)).isEqualTo(100);
            assertThat(purchaseRedisRepository.hasHold(dropId, orderId)).isFalse();
            assertThat(purchaseRedisRepository.hasPurchased(dropId, userId)).isTrue();
        }

        @Test
        @DisplayName("만료되지 않은 hold는 처리하지 않는다")
        void expireHolds_validHold_keepsHoldIntact() throws Exception {
            // holdTtlSec=300인 별도 드롭으로 검증 (setUp의 dropId와 분리)
            UUID validDropId = UUID.randomUUID();
            UUID validUserId = UUID.randomUUID();
            Drop validDrop = dropRepository.save(openDrop());
            validDropId = validDrop.getDropId();

            purchaseRedisRepository.warmup(validDropId, 100, 300, UUID.fromString(PRODUCT_ID));

            MvcResult result = mockMvc.perform(post("/api/v1/drops/{dropId}/purchase", validDropId)
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", validUserId.toString())
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isAccepted())
                    .andReturn();

            UUID validOrderId = UUID.fromString(
                    objectMapper.readTree(result.getResponse().getContentAsString())
                            .at("/data/orderId").asText()
            );

            holdExpireScheduler.expireHolds();

            // 300초 TTL → 만료 아님 → hold 유지
            assertThat(purchaseRedisRepository.hasHold(validDropId, validOrderId)).isTrue();
            assertThat(purchaseRedisRepository.getStock(validDropId)).isEqualTo(99);

            purchaseRedisRepository.deleteDropKeys(validDropId);
        }
    }

    // =========================================================================
    // 픽스처 헬퍼
    // =========================================================================

    private Drop scheduledDrop() {
        return Drop.create(
                UUID.fromString(PRODUCT_ID),
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(2),
                100,
                300
        );
    }

    private Drop openDrop() {
        Drop drop = Drop.create(
                UUID.fromString(PRODUCT_ID),
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusDays(1),
                100,
                300
        );
        drop.open();
        return drop;
    }

    private String future(int plusDays) {
        return LocalDateTime.now().plusDays(plusDays).toString();
    }
}
