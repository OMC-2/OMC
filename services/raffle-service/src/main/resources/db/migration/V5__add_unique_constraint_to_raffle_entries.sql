-- 동일 래플에 대해 동일 사용자가 중복 응모하지 못하도록 UNIQUE 제약 추가
ALTER TABLE p_raffle_entries ADD CONSTRAINT uk_raffle_user UNIQUE (raffle_id, user_id);
