SET search_path TO order_db;

-- ===========================================================================
-- 1. 주문 테이블 (p_orders)
-- ===========================================================================
CREATE TABLE IF NOT EXISTS p_orders (
                                        order_id            UUID            NOT NULL,
                                        user_id             UUID            NOT NULL,
                                        product_id          UUID            NOT NULL,
                                        drop_id             UUID,
                                        entry_id            UUID,
                                        payment_id          UUID,
                                        applied_coupon_id   UUID,
                                        raffle_id           UUID,
                                        order_type          VARCHAR(50)     NOT NULL,
                                        quantity            INT             NOT NULL DEFAULT 1,
                                        status              VARCHAR(50)     NOT NULL,
                                        original_amount     BIGINT          NOT NULL,
                                        discount_amount     BIGINT          NOT NULL DEFAULT 0,
                                        final_amount        BIGINT          NOT NULL,
                                        cancel_reason       VARCHAR(50),
                                        idempotency_key     VARCHAR(255),

                                        created_at          TIMESTAMP       NOT NULL,
                                        paid_at             TIMESTAMP,
                                        confirmed_at        TIMESTAMP,
                                        cancelled_at        TIMESTAMP,
                                        refunded_at         TIMESTAMP,
                                        shipped_at          TIMESTAMP,
                                        delivered_at        TIMESTAMP,
                                        expires_at          TIMESTAMP,
                                        refundable_until    TIMESTAMP,

                                        version             INT             NOT NULL DEFAULT 0,
                                        created_by          UUID,
                                        updated_at          TIMESTAMP,
                                        updated_by          UUID,
                                        deleted_at          TIMESTAMP,
                                        deleted_by          UUID,

                                        CONSTRAINT pk_p_orders PRIMARY KEY (order_id),
                                        CONSTRAINT chk_p_orders_quantity CHECK (quantity > 0),
                                        CONSTRAINT chk_p_orders_amounts CHECK (final_amount >= 0 AND discount_amount >= 0 AND original_amount >= 0)
);

CREATE INDEX IF NOT EXISTS idx_p_orders_user_id ON p_orders (user_id);
CREATE INDEX IF NOT EXISTS idx_p_orders_status ON p_orders (status);
CREATE INDEX IF NOT EXISTS idx_p_orders_created_at ON p_orders (created_at);


-- ===========================================================================
-- 2. 컨슈머 멱등성 방어 테이블 (p_order_processed_events)
-- ===========================================================================
CREATE TABLE IF NOT EXISTS p_order_processed_events (
                                                        event_id        VARCHAR(100)    NOT NULL,
                                                        topic           VARCHAR(50)     NOT NULL,
                                                        processed_at    TIMESTAMP       NOT NULL,

                                                        CONSTRAINT pk_p_order_processed_events PRIMARY KEY (event_id)
);


-- ============================================================
-- 3. order-service Transactional Outbox 테이블 DDL (PostgreSQL)
-- ============================================================
-- ERD의 p_order_outbox_events 기반.
-- 단, 발행기(Poller)가 어느 토픽으로 보낼지 알아야 하므로 topic 컬럼을 추가했다.
-- 핵심: event_id(PK) == payload 안의 eventId == 컨슈머 멱등성 키
-- ============================================================

CREATE TABLE IF NOT EXISTS p_order_outbox_events (
                                                     event_id        UUID         NOT NULL,                 -- 이벤트 고유 ID (= 컨슈머 멱등성 키, payload 내 eventId와 동일)
                                                     aggregate_type  VARCHAR(100) NOT NULL,                 -- 대상 도메인 (예: ORDER)
                                                     aggregate_id    UUID         NOT NULL,                 -- 대상 고유 ID (예: orderId)
                                                     event_type      VARCHAR(100) NOT NULL,                 -- 발행 이벤트 (예: ORDER_CREATED)
                                                     topic           VARCHAR(100) NOT NULL,                 -- Kafka 발행 대상 토픽 (예: order.created)
                                                     payload         TEXT         NOT NULL,                 -- Kafka 전송 JSON (event_id 포함 필수)
                                                     status          VARCHAR(50)  NOT NULL DEFAULT 'INIT',  -- INIT / PUBLISHED / FAILED
                                                     retry_count     INT          NOT NULL DEFAULT 0,       -- 발행 재시도 횟수
                                                     created_at      TIMESTAMP    NOT NULL,                 -- 생성 일시
                                                     published_at    TIMESTAMP    NULL,                     -- Kafka ACK 수신 일시 (INIT 상태에서는 NULL)
                                                     CONSTRAINT pk_p_order_outbox_events PRIMARY KEY (event_id)
);

-- Poller가 INIT 상태를 생성순으로 조회하므로 (status, created_at) 복합 인덱스
CREATE INDEX IF NOT EXISTS idx_order_outbox_status_created
    ON p_order_outbox_events (status, created_at);


-- ===========================================================================
-- 4. DLQ 테이블 (p_order_dlq_messages)
-- ===========================================================================
CREATE TABLE IF NOT EXISTS p_order_dlq_messages (
                                                    dlq_id          UUID            NOT NULL,
                                                    topic           VARCHAR(100)    NOT NULL,
                                                    partition_id    INT             NOT NULL,
                                                    offset_value    BIGINT          NOT NULL,
                                                    message_key     VARCHAR(255),
                                                    payload         TEXT            NOT NULL,
                                                    error_class     VARCHAR(255)    NOT NULL,
                                                    error_message   TEXT,
                                                    status          VARCHAR(50)     NOT NULL, -- FAILED, RESOLVED
                                                    republish_count INT             NOT NULL DEFAULT 0,
                                                    failed_at       TIMESTAMP       NOT NULL,
                                                    resolved_at     TIMESTAMP,

                                                    CONSTRAINT pk_p_order_dlq_messages PRIMARY KEY (dlq_id)
);

CREATE INDEX IF NOT EXISTS idx_order_dlq_status ON p_order_dlq_messages (status);