-- raffle-service를 drop-service로부터 독립시키기 위해 drop_id 컬럼 제거
-- Raffle은 productId만으로 주문 생성에 필요한 정보를 충분히 보유함

DROP INDEX IF EXISTS idx_p_raffles_drop_id;

ALTER TABLE p_raffles DROP COLUMN IF EXISTS drop_id;
