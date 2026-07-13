package com.omc.drop.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.drop.application.scheduler.DropOutboxPoller;
import com.omc.drop.application.scheduler.HoldExpireScheduler;
import com.omc.drop.domain.entity.Drop;
import com.omc.drop.domain.entity.DropOutboxEvent;
import com.omc.drop.domain.enums.DropOutboxStatus;
import com.omc.drop.domain.repository.DropOutboxEventRepository;
import com.omc.drop.domain.repository.DropProcessedEventRepository;
import com.omc.drop.domain.repository.DropPurchaseReservationRepository;
import com.omc.drop.domain.repository.DropRepository;
import com.omc.drop.application.event.consumer.PaymentCompletedEvent;
import com.omc.drop.application.event.consumer.PaymentFailedEvent;
import com.omc.drop.application.event.consumer.StockFailedEvent;
import com.omc.drop.infrastructure.redis.DropRedisStore;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.util.List;
import java.util.Map;
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
            .withPassword("test")
            ;

    @SuppressWarnings("resource")
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7.2-alpine")
            .withExposedPorts(6379)
            .withTmpFs(Map.of("/data", "rw"));

    static {
        postgres.start();
        redis.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl() + "?currentSchema=drop_db");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired DropRepository dropRepository;
    @Autowired DropRedisStore dropRedisStore;
    @Autowired DropProcessedEventRepository processedEventRepository;
    @Autowired DropPurchaseReservationRepository reservationRepository;
    @Autowired DropOutboxEventRepository outboxRepository;
    @Autowired DropOutboxPoller dropOutboxPoller;
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
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
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
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
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
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
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
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
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
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
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
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
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
            dropId = UUID.randomUUID();
        }

        @AfterEach
        void tearDown() {
            dropRedisStore.deleteDropKeys(dropId);
            outboxRepository.deleteAll();
            reservationRepository.deleteAll();
        }

        @Test
        @DisplayName("OPEN 드롭에 구매 선점 시 202와 orderId, queueNumber를 반환하고 Redis 상태가 변경된다")
        void purchase_openDrop_returns202_and_redisStateUpdated() throws Exception {
            dropRedisStore.warmup(dropId, 100, 300, UUID.fromString(PRODUCT_ID));

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

            assertThat(dropRedisStore.getStock(dropId)).isEqualTo(99);
            assertThat(dropRedisStore.hasPurchased(dropId, UUID.fromString(USER_ID))).isTrue();
            assertThat(dropRedisStore.hasHold(dropId, orderId)).isTrue();
        }

        @Test
        @DisplayName("구매 선점 성공 시 DB에 예약 레코드와 outbox 이벤트가 저장된다")
        void purchase_openDrop_savesReservationAndOutbox() throws Exception {
            dropRedisStore.warmup(dropId, 100, 300, UUID.fromString(PRODUCT_ID));

            MvcResult result = mockMvc.perform(post("/api/v1/drops/{dropId}/purchase", dropId)
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isAccepted())
                    .andReturn();

            UUID orderId = UUID.fromString(
                    objectMapper.readTree(result.getResponse().getContentAsString())
                            .at("/data/orderId").asText()
            );

            // 예약 레코드 검증
            assertThat(reservationRepository.findById(orderId)).isPresent().hasValueSatisfying(r -> {
                assertThat(r.getDropId()).isEqualTo(dropId);
                assertThat(r.getUserId()).isEqualTo(UUID.fromString(USER_ID));
                assertThat(r.getProductId()).isEqualTo(UUID.fromString(PRODUCT_ID));
                assertThat(r.getQueueNumber()).isEqualTo(1L);
            });

            // outbox 이벤트 검증
            List<DropOutboxEvent> outboxEvents = outboxRepository.findAll();
            assertThat(outboxEvents).hasSize(1).first().satisfies(e -> {
                assertThat(e.getStatus()).isEqualTo(DropOutboxStatus.INIT);
                assertThat(e.getTopic()).isEqualTo("purchase.confirmed");
                assertThat(e.getAggregateId()).isEqualTo(orderId);
                assertThat(e.getPayload()).contains(orderId.toString());
                assertThat(e.getPayload()).contains(PRODUCT_ID);
            });
        }

        @Test
        @DisplayName("같은 사용자가 중복 구매 시 409와 DROP-005를 반환한다")
        void purchase_duplicate_returns409() throws Exception {
            dropRedisStore.warmup(dropId, 100, 300, UUID.fromString(PRODUCT_ID));

            mockMvc.perform(post("/api/v1/drops/{dropId}/purchase", dropId)
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isAccepted());

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
            dropRedisStore.warmup(dropId, 1, 300, UUID.fromString(PRODUCT_ID));

            String otherUserId = UUID.randomUUID().toString();

            mockMvc.perform(post("/api/v1/drops/{dropId}/purchase", dropId)
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", otherUserId)
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isAccepted());

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
            dropRedisStore.warmup(dropId, 100, 300, UUID.fromString(PRODUCT_ID));

            mockMvc.perform(post("/api/v1/drops/{dropId}/purchase", dropId))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("warmup을 두 번 호출해도 재고가 초기화되지 않는다 (Lua 멱등성)")
        void warmup_calledTwice_doesNotResetStock() throws Exception {
            dropRedisStore.warmup(dropId, 100, 300, UUID.fromString(PRODUCT_ID));

            mockMvc.perform(post("/api/v1/drops/{dropId}/purchase", dropId)
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isAccepted());

            assertThat(dropRedisStore.getStock(dropId)).isEqualTo(99);

            dropRedisStore.warmup(dropId, 100, 300, UUID.fromString(PRODUCT_ID));

            assertThat(dropRedisStore.getStock(dropId)).isEqualTo(99);
        }
    }

    // =========================================================================
    // Outbox Poller — purchase.confirmed 발행
    // =========================================================================

    @Nested
    @DisplayName("DropOutboxPoller 테스트")
    class OutboxPollerTests {

        private UUID dropId;

        @BeforeEach
        void setUp() {
            dropId = UUID.randomUUID();
        }

        @AfterEach
        void tearDown() {
            dropRedisStore.deleteDropKeys(dropId);
            outboxRepository.deleteAll();
            reservationRepository.deleteAll();
        }

        @Test
        @DisplayName("INIT 상태 outbox 이벤트가 포ller 실행 후 PUBLISHED로 변경된다")
        void publishPending_marksOutboxPublished() throws Exception {
            dropRedisStore.warmup(dropId, 100, 300, UUID.fromString(PRODUCT_ID));

            mockMvc.perform(post("/api/v1/drops/{dropId}/purchase", dropId)
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isAccepted());

            assertThat(outboxRepository.findAll()).hasSize(1)
                    .first().extracting(DropOutboxEvent::getStatus)
                    .isEqualTo(DropOutboxStatus.INIT);

            // Poller 직접 호출
            dropOutboxPoller.publishPending();

            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(outboxRepository.findAll()).hasSize(1)
                            .first().extracting(DropOutboxEvent::getStatus)
                            .isEqualTo(DropOutboxStatus.PUBLISHED)
            );
        }

        @Test
        @DisplayName("outbox가 없으면 Poller가 아무 것도 하지 않는다")
        void publishPending_noOutbox_doesNothing() {
            assertThat(outboxRepository.findAll()).isEmpty();
            dropOutboxPoller.publishPending(); // 예외 없이 완료
        }
    }

    // =========================================================================
    // Kafka Consumer — payment.completed / payment.failed / stock.failed
    // =========================================================================

    @Nested
    @DisplayName("Kafka Consumer 테스트")
    class ConsumerTests {

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

            dropRedisStore.warmup(dropId, 100, 300, UUID.fromString(PRODUCT_ID));

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
            dropRedisStore.deleteDropKeys(dropId);
            processedEventRepository.deleteAll();
            outboxRepository.deleteAll();
            reservationRepository.deleteAll();
        }

        @Test
        @DisplayName("payment.completed(DROP) 수신 시 hold가 제거되고 재고는 유지된다")
        void onPaymentCompleted_instant_removesHold() throws Exception {
            String eventId = UUID.randomUUID().toString();
            PaymentCompletedEvent event = new PaymentCompletedEvent(
                    eventId, "DROP", userId, null,
                    10000L, 0L, 10000L, orderId, dropId
            );

            stringKafkaTemplate.send("payment.completed", objectMapper.writeValueAsString(event));

            await().atMost(5, TimeUnit.SECONDS)
                    .until(() -> !dropRedisStore.hasHold(dropId, orderId));

            assertThat(dropRedisStore.hasHold(dropId, orderId)).isFalse();
            assertThat(dropRedisStore.getStock(dropId)).isEqualTo(99);
            assertThat(dropRedisStore.hasPurchased(dropId, userId)).isTrue();
            assertThat(processedEventRepository.existsById(eventId)).isTrue();
        }

        @Test
        @DisplayName("payment.completed 후 늦은 payment.failed는 purchased set을 지우지 않는다")
        void latePaymentFailed_afterCompleted_keepsPurchasedMarker() throws Exception {
            String completedEventId = UUID.randomUUID().toString();
            PaymentCompletedEvent completed = new PaymentCompletedEvent(
                    completedEventId, "DROP", userId, null,
                    10000L, 0L, 10000L, orderId, dropId
            );

            stringKafkaTemplate.send("payment.completed", objectMapper.writeValueAsString(completed));
            await().atMost(5, TimeUnit.SECONDS)
                    .until(() -> !dropRedisStore.hasHold(dropId, orderId));

            String failedEventId = UUID.randomUUID().toString();
            PaymentFailedEvent failed = new PaymentFailedEvent(
                    failedEventId, "DROP", userId, "LATE_FAILURE", orderId, dropId
            );

            stringKafkaTemplate.send("payment.failed", objectMapper.writeValueAsString(failed));
            await().atMost(5, TimeUnit.SECONDS)
                    .until(() -> processedEventRepository.existsById(failedEventId));

            assertThat(dropRedisStore.getStock(dropId)).isEqualTo(99);
            assertThat(dropRedisStore.hasPurchased(dropId, userId)).isTrue();
            assertThat(dropRedisStore.hasHold(dropId, orderId)).isFalse();
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

            Thread.sleep(2000);

            assertThat(dropRedisStore.hasHold(dropId, orderId)).isTrue();
            assertThat(processedEventRepository.existsById(eventId)).isFalse();
        }

        @Test
        @DisplayName("payment.failed(DROP) 수신 시 재고가 복구되고 구매자가 취소된다")
        void onPaymentFailed_instant_recoversStock() throws Exception {
            String eventId = UUID.randomUUID().toString();
            PaymentFailedEvent event = new PaymentFailedEvent(
                    eventId, "DROP", userId, "PAYMENT_TIMEOUT", orderId, dropId
            );

            stringKafkaTemplate.send("payment.failed", objectMapper.writeValueAsString(event));

            await().atMost(5, TimeUnit.SECONDS)
                    .until(() -> dropRedisStore.getStock(dropId) == 100);

            assertThat(dropRedisStore.getStock(dropId)).isEqualTo(100);
            assertThat(dropRedisStore.hasPurchased(dropId, userId)).isFalse();
            assertThat(dropRedisStore.hasHold(dropId, orderId)).isFalse();
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
                    .until(() -> dropRedisStore.getStock(dropId) == 100);

            assertThat(dropRedisStore.getStock(dropId)).isEqualTo(100);
            assertThat(dropRedisStore.hasPurchased(dropId, userId)).isFalse();
            assertThat(dropRedisStore.hasHold(dropId, orderId)).isFalse();
            assertThat(processedEventRepository.existsById(eventId)).isTrue();
        }

        @Test
        @DisplayName("동일 eventId 수신 시 두 번째 이벤트를 스킵한다")
        void duplicateEvent_skipsSecondProcessing() throws Exception {
            String eventId = UUID.randomUUID().toString();
            PaymentFailedEvent event = new PaymentFailedEvent(
                    eventId, "DROP", userId, "PAYMENT_TIMEOUT", orderId, dropId
            );
            String message = objectMapper.writeValueAsString(event);

            stringKafkaTemplate.send("payment.failed", message);
            await().atMost(5, TimeUnit.SECONDS)
                    .until(() -> processedEventRepository.existsById(eventId));

            stringKafkaTemplate.send("payment.failed", message);
            Thread.sleep(2000);

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

            Drop savedDrop = dropRepository.save(openDrop());
            dropId = savedDrop.getDropId();

            dropRedisStore.warmup(dropId, 100, 1, UUID.fromString(PRODUCT_ID));

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
            dropRedisStore.deleteDropKeys(dropId);
            outboxRepository.deleteAll();
            reservationRepository.deleteAll();
        }

        @Test
        @DisplayName("만료된 hold는 재고를 복구하고 hold를 제거한다")
        void expireHolds_expiredHold_recoversStockAndRemovesHold() throws Exception {
            Thread.sleep(2000);

            holdExpireScheduler.expireHolds();

            assertThat(dropRedisStore.getStock(dropId)).isEqualTo(100);
            assertThat(dropRedisStore.hasHold(dropId, orderId)).isFalse();
            assertThat(dropRedisStore.hasPurchased(dropId, userId)).isTrue();
        }

        @Test
        @DisplayName("만료되지 않은 hold는 처리하지 않는다")
        void expireHolds_validHold_keepsHoldIntact() throws Exception {
            UUID validDropId;
            UUID validUserId = UUID.randomUUID();
            Drop validDrop = dropRepository.save(openDrop());
            validDropId = validDrop.getDropId();

            dropRedisStore.warmup(validDropId, 100, 300, UUID.fromString(PRODUCT_ID));

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

            assertThat(dropRedisStore.hasHold(validDropId, validOrderId)).isTrue();
            assertThat(dropRedisStore.getStock(validDropId)).isEqualTo(99);

            dropRedisStore.deleteDropKeys(validDropId);
            outboxRepository.deleteAll();
            reservationRepository.deleteAll();
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
