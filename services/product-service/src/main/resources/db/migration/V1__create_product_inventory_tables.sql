SET search_path TO product_db;
-- ========================================
-- p_products
-- ========================================
CREATE TABLE p_products (
                            product_id    UUID         NOT NULL DEFAULT uuidv7(),
                            name          VARCHAR(100) NOT NULL,
                            description   TEXT,
                            price         BIGINT       NOT NULL,
                            brand         VARCHAR(50)  NOT NULL,
                            category      VARCHAR(30)  NOT NULL,
                            image_url     VARCHAR(500),
                            status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
                            created_at    TIMESTAMP    NOT NULL,
                            created_by    UUID,
                            updated_at    TIMESTAMP,
                            updated_by    UUID,
                            deleted_at    TIMESTAMP,
                            deleted_by    UUID,
                            PRIMARY KEY (product_id)
);

CREATE INDEX idx_products_category        ON p_products (category);
CREATE INDEX idx_products_status          ON p_products (status);
CREATE INDEX idx_products_category_status ON p_products (category, status);

-- ========================================
-- p_inventories
-- ========================================
CREATE TABLE p_inventories (
                               inventory_id       UUID   NOT NULL DEFAULT uuidv7(),
                               product_id         UUID   NOT NULL UNIQUE,
                               total_quantity     INT    NOT NULL,
                               sold_quantity      INT    NOT NULL DEFAULT 0,
                               available_quantity INT GENERATED ALWAYS AS (total_quantity - sold_quantity) STORED,
                               version            BIGINT NOT NULL DEFAULT 0,
                               created_at         TIMESTAMP NOT NULL,
                               created_by         UUID,
                               updated_at         TIMESTAMP,
                               updated_by         UUID,
                               deleted_at         TIMESTAMP,
                               deleted_by         UUID,
                               PRIMARY KEY (inventory_id)
);

-- ========================================
-- p_processed_events (멱등성 방어)
-- ========================================
CREATE TABLE p_processed_events (
                                    event_id     VARCHAR(50)  NOT NULL,
                                    topic        VARCHAR(100) NOT NULL,
                                    processed_at TIMESTAMP    NOT NULL,
                                    PRIMARY KEY (event_id)
);

-- ========================================
-- p_outbox_events (Transactional Outbox)
-- ========================================
CREATE TABLE p_outbox_events (
                                 event_id      UUID         NOT NULL DEFAULT uuidv7(),
                                 aggregate_type VARCHAR(100) NOT NULL,
                                 aggregate_id  UUID         NOT NULL,
                                 event_type    VARCHAR(100) NOT NULL,
                                 payload       TEXT         NOT NULL,
                                 status        VARCHAR(50)  NOT NULL DEFAULT 'INIT',
                                 retry_count   INT          NOT NULL DEFAULT 0,
                                 created_at    TIMESTAMP    NOT NULL,
                                 published_at  TIMESTAMP,
                                 PRIMARY KEY (event_id)
);

CREATE INDEX idx_outbox_status_created_at ON p_outbox_events (status, created_at);

-- ========================================
-- p_failed_event_logs (실패 이벤트 로그)
-- ========================================
CREATE TABLE p_failed_event_logs (
                                     log_id           UUID         NOT NULL DEFAULT uuidv7(),
                                     original_topic   VARCHAR(255) NOT NULL,
                                     consumer_group   VARCHAR(255) NOT NULL,
                                     aggregate_type   VARCHAR(100) NOT NULL,
                                     aggregate_id     UUID         NOT NULL,
                                     original_payload TEXT         NOT NULL,
                                     error_message    TEXT         NOT NULL,
                                     status           VARCHAR(50)  NOT NULL DEFAULT 'UNRESOLVED',
                                     created_at       TIMESTAMP    NOT NULL,
                                     resolved_at      TIMESTAMP,
                                     resolved_by      UUID,
                                     PRIMARY KEY (log_id)
);

CREATE INDEX idx_failed_logs_status ON p_failed_event_logs (status);