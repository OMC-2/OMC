-- 1. p_raffles 테이블에 draw_seed 컬럼 추가
ALTER TABLE p_raffles ADD COLUMN draw_seed VARCHAR(255);

-- 2. p_raffle_penalties 테이블 생성
CREATE TABLE p_raffle_penalties (
    penalty_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    raffle_id UUID NOT NULL,
    penalty_end_date TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP
);


-- 3. p_raffle_penalties 인덱스 추가
CREATE INDEX idx_raffle_penalty_user_id ON p_raffle_penalties (user_id);
