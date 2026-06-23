CREATE SCHEMA IF NOT EXISTS notification_db;
SET search_path TO notification_db;

CREATE TABLE p_notifications (
    notification_id   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID         NOT NULL,
    slack_id          VARCHAR(100) NOT NULL,
    notification_type VARCHAR(50)  NOT NULL,
    title             VARCHAR(200) NOT NULL,
    content           TEXT         NOT NULL,
    reference_id      UUID,
    reference_type    VARCHAR(50),
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count       INT          NOT NULL DEFAULT 0,
    is_read           BOOLEAN      NOT NULL DEFAULT FALSE,
    sent_at           TIMESTAMP,
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    created_by        UUID,
    updated_at        TIMESTAMP    NOT NULL DEFAULT now(),
    updated_by        UUID,
    deleted_at        TIMESTAMP,
    deleted_by        UUID
);

CREATE INDEX idx_notifications_user_id ON p_notifications (user_id);
CREATE INDEX idx_notifications_status  ON p_notifications (status, created_at);

CREATE TABLE p_notification_processed_events (
    event_id     VARCHAR(50)  NOT NULL PRIMARY KEY,
    topic        VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP    NOT NULL
);
