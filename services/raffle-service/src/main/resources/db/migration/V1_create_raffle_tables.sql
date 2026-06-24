-- ===========================================================================
-- 1. 래플 테이블 (p_raffles)
-- ===========================================================================
CREATE TABLE IF NOT EXISTS p_raffles (
    raffle_id       UUID            NOT NULL,
    drop_id         UUID            NOT NULL,
    product_id      UUID            NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    winner_count    INT             NOT NULL,
    status          VARCHAR(20)     NOT NULL,
    started_at      TIMESTAMP       NOT NULL,
    ended_at        TIMESTAMP       NOT NULL,
    created_at      TIMESTAMP       NOT NULL,
    updated_at      TIMESTAMP,
    deleted_at      TIMESTAMP,
    CONSTRAINT pk_p_raffles PRIMARY KEY (raffle_id)
);

CREATE INDEX IF NOT EXISTS idx_p_raffles_status ON p_raffles (status);
CREATE INDEX IF NOT EXISTS idx_p_raffles_drop_id ON p_raffles (drop_id);


-- ===========================================================================
-- 2. 래플 응모 테이블 (p_raffle_entries)
-- ===========================================================================
CREATE TABLE IF NOT EXISTS p_raffle_entries (
    entry_id            UUID            NOT NULL,
    raffle_id           UUID            NOT NULL,
    user_id             UUID            NOT NULL,
    billing_key_id      VARCHAR(255)    NOT NULL,
    coupon_id           UUID,
    original_amount     NUMERIC(19, 2)  NOT NULL,
    discount_amount     NUMERIC(19, 2)  NOT NULL,
    final_amount        NUMERIC(19, 2)  NOT NULL,
    entered_at          TIMESTAMP       NOT NULL,
    created_at          TIMESTAMP       NOT NULL,
    updated_at          TIMESTAMP,
    deleted_at          TIMESTAMP,
    CONSTRAINT pk_p_raffle_entries PRIMARY KEY (entry_id)
);

CREATE INDEX IF NOT EXISTS idx_p_raffle_entries_raffle_id ON p_raffle_entries (raffle_id);
CREATE INDEX IF NOT EXISTS idx_p_raffle_entries_user_id   ON p_raffle_entries (user_id);


-- ===========================================================================
-- 3. 래플 결과 테이블 (p_raffle_results)
-- ===========================================================================
CREATE TABLE IF NOT EXISTS p_raffle_results (
    result_id   UUID        NOT NULL,
    entry_id    UUID        NOT NULL,
    raffle_id   UUID        NOT NULL,
    user_id     UUID        NOT NULL,
    result      VARCHAR(20) NOT NULL,
    decided_at  TIMESTAMP   NOT NULL,
    created_at  TIMESTAMP   NOT NULL,
    updated_at  TIMESTAMP,
    deleted_at  TIMESTAMP,
    CONSTRAINT pk_p_raffle_results PRIMARY KEY (result_id)
);

CREATE INDEX IF NOT EXISTS idx_p_raffle_results_raffle_id ON p_raffle_results (raffle_id);
CREATE INDEX IF NOT EXISTS idx_p_raffle_results_user_id   ON p_raffle_results (user_id);


-- ===========================================================================
-- 4. Transactional Outbox 테이블 (p_outbox_events)
-- ===========================================================================
CREATE TABLE IF NOT EXISTS p_outbox_events (
    event_id        UUID            NOT NULL,
    aggregate_id    VARCHAR(255)    NOT NULL,
    aggregate_type  VARCHAR(255)    NOT NULL,
    event_type      VARCHAR(255)    NOT NULL,
    payload         TEXT            NOT NULL,
    status          VARCHAR(20)     NOT NULL,
    retry_count     INT             NOT NULL DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL,
    updated_at      TIMESTAMP,
    deleted_at      TIMESTAMP,
    CONSTRAINT pk_p_outbox_events PRIMARY KEY (event_id)
);

CREATE INDEX IF NOT EXISTS idx_p_outbox_events_status ON p_outbox_events (status, created_at);
