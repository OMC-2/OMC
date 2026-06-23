-- ========================
-- uuidv7 function
-- ========================
CREATE OR REPLACE FUNCTION uuidv7()
RETURNS UUID
LANGUAGE plpgsql
AS $$
DECLARE
    unix_ts_ms BIGINT;
    ts_hex TEXT;
    rand_hex TEXT;
    variant_hex TEXT;
BEGIN
    unix_ts_ms := FLOOR(EXTRACT(EPOCH FROM clock_timestamp()) * 1000);
    ts_hex := lpad(to_hex(unix_ts_ms), 12, '0');
    rand_hex := encode(gen_random_bytes(9), 'hex');
    variant_hex := substr('89ab', (get_byte(gen_random_bytes(1), 0) % 4) + 1, 1);

    RETURN (
        substr(ts_hex, 1, 8) || '-' ||
        substr(ts_hex, 9, 4) || '-' ||
        '7' || substr(rand_hex, 1, 3) || '-' ||
        variant_hex || substr(rand_hex, 4, 3) || '-' ||
        substr(rand_hex, 7, 12)
    )::uuid;
END;
$$;

-- ========================
-- p_payments
-- ========================
ALTER TABLE p_payments
    ADD COLUMN IF NOT EXISTS drop_id UUID,
    ADD COLUMN IF NOT EXISTS raffle_id UUID,
    ADD COLUMN IF NOT EXISTS product_id UUID;

ALTER TABLE p_payments
    ALTER COLUMN payment_id SET DEFAULT uuidv7();

-- ========================
-- p_payment_outbox_events
-- ========================
CREATE TABLE p_payment_outbox_events (
    event_id        UUID         NOT NULL DEFAULT uuidv7(),
    aggregate_type  VARCHAR(50)  NOT NULL,
    aggregate_id    UUID         NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         TEXT         NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    retry_count     INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at    TIMESTAMP,
    PRIMARY KEY (event_id)
);

CREATE INDEX idx_payment_outbox_status_retry_created_at
    ON p_payment_outbox_events (status, retry_count, created_at);

CREATE INDEX idx_payment_outbox_aggregate_id
    ON p_payment_outbox_events (aggregate_id);

-- ========================
-- p_payment_inbox_events
-- ========================
CREATE TABLE p_payment_inbox_events (
    event_id      VARCHAR(100) NOT NULL,
    topic         VARCHAR(100) NOT NULL,
    processed_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id)
);

CREATE INDEX idx_payment_inbox_topic_processed_at
    ON p_payment_inbox_events (topic, processed_at);
