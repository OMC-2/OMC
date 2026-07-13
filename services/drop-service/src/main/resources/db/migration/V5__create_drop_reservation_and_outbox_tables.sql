CREATE TABLE p_drop_purchase_reservations (
    order_id        UUID        NOT NULL,
    drop_id         UUID        NOT NULL,
    user_id         UUID        NOT NULL,
    product_id      UUID        NOT NULL,
    hold_expires_at TIMESTAMPTZ NOT NULL,
    queue_number    BIGINT      NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_drop_purchase_reservations  PRIMARY KEY (order_id),
    CONSTRAINT uq_drop_reservations_drop_user UNIQUE (drop_id, user_id)
);

CREATE INDEX idx_drop_reservations_drop_id ON p_drop_purchase_reservations (drop_id);
CREATE INDEX idx_drop_reservations_user_id ON p_drop_purchase_reservations (user_id);

CREATE TABLE p_drop_outbox_events (
    event_id       UUID         NOT NULL,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(50)  NOT NULL,
    topic          VARCHAR(100) NOT NULL,
    payload        TEXT         NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'INIT',
    retry_count    INT          NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,

    CONSTRAINT pk_drop_outbox_events PRIMARY KEY (event_id),
    CONSTRAINT chk_drop_outbox_status CHECK (status IN ('INIT', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX idx_drop_outbox_status_created ON p_drop_outbox_events (status, created_at);
