-- V1이 baseline으로 처리되어 p_raffle_results에 누락된 컬럼 추가
ALTER TABLE p_raffle_results ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;
ALTER TABLE p_raffle_results ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
