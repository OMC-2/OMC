-- Kafka Consumer 멱등성 보장을 위한 처리 완료 이벤트 기록 테이블
-- event_id (PK) 기반으로 중복 수신된 이벤트를 식별하여 재처리를 방지합니다.
CREATE TABLE IF NOT EXISTS p_raffle_processed_events (
    event_id     VARCHAR(100) NOT NULL,
    topic        VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP    NOT NULL,
    CONSTRAINT pk_raffle_processed_events PRIMARY KEY (event_id)
);
