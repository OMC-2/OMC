CREATE TABLE p_drop_processed_events (
    event_id     VARCHAR(100) NOT NULL,
    topic        VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_drop_processed_events PRIMARY KEY (event_id)
);
