CREATE TABLE p_drops (
    drop_id      UUID         NOT NULL DEFAULT gen_random_uuid(),
    product_id   UUID         NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',
    start_at     TIMESTAMPTZ  NOT NULL,
    end_at       TIMESTAMPTZ  NOT NULL,
    total_qty    INT          NOT NULL,
    hold_ttl_sec INT          NOT NULL DEFAULT 600,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   VARCHAR(100),          -- TODO: BaseEntity 적용 후 NOT NULL 제약 추가
    updated_at   TIMESTAMPTZ,
    updated_by   VARCHAR(100),

    CONSTRAINT pk_drops         PRIMARY KEY (drop_id),
    CONSTRAINT chk_drops_status CHECK (status IN ('SCHEDULED', 'OPEN', 'CLOSED')),
    CONSTRAINT chk_drops_qty    CHECK (total_qty > 0),
    CONSTRAINT chk_drops_period CHECK (end_at > start_at)
);

CREATE INDEX idx_drops_status_start ON p_drops (status, start_at);
CREATE INDEX idx_drops_status_end   ON p_drops (status, end_at);
