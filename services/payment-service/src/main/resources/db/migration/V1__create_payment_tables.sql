-- ========================
-- p_payments
-- ========================
CREATE TABLE p_payments (
    payment_id         UUID         NOT NULL DEFAULT gen_random_uuid(),
    order_id           UUID,
    user_id            UUID         NOT NULL,
    entry_id           UUID,
    sales_type         VARCHAR(20)  NOT NULL,
    original_amount    BIGINT       NOT NULL,
    discount_amount    BIGINT,
    final_amount       BIGINT       NOT NULL,
    provider           VARCHAR(20)  NOT NULL,
    provier_payment_id VARCHAR(200),
    payment_method     VARCHAR(20)  NOT NULL,
    payment_status     VARCHAR(50)  NOT NULL,
    failure_code       VARCHAR(100),
    failure_message    TEXT,
    cancellation_code  INTEGER,
    cancelled_message  TEXT,
    version            BIGINT       NOT NULL DEFAULT 0,
    requested_at       TIMESTAMP,
    approved_at        TIMESTAMP,
    failed_at          TIMESTAMP,
    canceled_at        TIMESTAMP,
    refunded_at        TIMESTAMP,
    created_at         TIMESTAMP    NOT NULL,
    created_by         UUID,
    updated_at         TIMESTAMP,
    updated_by         UUID,
    deleted_at         TIMESTAMP,
    deleted_by         UUID,
    PRIMARY KEY (payment_id),
    CONSTRAINT uk_payments_order_id UNIQUE (order_id),
    CONSTRAINT uk_payments_entry_id UNIQUE (entry_id),
    CONSTRAINT chk_payments_reference_id CHECK (
        (sales_type = 'DROP' AND order_id IS NOT NULL AND entry_id IS NULL) OR
        (sales_type = 'RAFFLE' AND order_id IS NULL AND entry_id IS NOT NULL)
    )
);
