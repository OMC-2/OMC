CREATE SCHEMA IF NOT EXISTS coupon_db;
SET search_path TO coupon_db;

-- 쿠폰 마스터
CREATE TABLE p_coupons (
    coupon_id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(100) NOT NULL,
    discount_type       VARCHAR(20)  NOT NULL,
    discount_value      DECIMAL(10,2) NOT NULL,
    max_discount_amount DECIMAL(10,2),
    total_quantity      INT          NOT NULL,
    remaining_quantity  INT          NOT NULL,
    started_at          TIMESTAMP    NOT NULL,
    expired_at          TIMESTAMP    NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_at          TIMESTAMP    NOT NULL DEFAULT now(),
    updated_by          UUID,
    deleted_at          TIMESTAMP,
    deleted_by          UUID
);

-- 유저 보유 쿠폰
CREATE TABLE p_user_coupons (
    user_coupon_id  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL,
    coupon_id       UUID        NOT NULL REFERENCES p_coupons(coupon_id),
    status          VARCHAR(20) NOT NULL,
    order_id        UUID,
    entry_id        UUID,
    used_at         TIMESTAMP,
    expired_at      TIMESTAMP   NOT NULL,
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_by      UUID,
    deleted_at      TIMESTAMP,
    deleted_by      UUID,
    CONSTRAINT uq_user_coupon UNIQUE (user_id, coupon_id)
);

CREATE INDEX idx_user_coupons_user_id ON p_user_coupons(user_id);
CREATE INDEX idx_user_coupons_order_id ON p_user_coupons(order_id);
CREATE INDEX idx_user_coupons_status ON p_user_coupons(status);
CREATE INDEX idx_user_coupons_expired_at ON p_user_coupons(expired_at);

-- 아웃박스 (이벤트 발행 보장)
CREATE TABLE p_coupon_outbox (
    event_id        UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    UUID        NOT NULL,
    event_type      VARCHAR(50) NOT NULL,
    payload         TEXT        NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'INIT',
    retry_count     INT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    published_at    TIMESTAMP
);

CREATE INDEX idx_coupon_outbox_status ON p_coupon_outbox(status);

-- 멱등성 방어 (Consumer 중복 처리 방지)
CREATE TABLE p_coupon_processed_events (
    event_id        VARCHAR(50) PRIMARY KEY,
    topic           VARCHAR(50) NOT NULL,
    processed_at    TIMESTAMP   NOT NULL
);
