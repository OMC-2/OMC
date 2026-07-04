-- Hibernate이 초기 DDL 생성 시 INIT/PUBLISHED/FAILED만 포함한 check constraint를 만든 후
-- PUBLISHING, DEAD가 enum에 추가됐지만 constraint가 업데이트되지 않았다.
-- 신규 설치 환경에는 constraint가 없으므로 IF EXISTS로 안전하게 처리한다.
ALTER TABLE p_payment_outbox_events
    DROP CONSTRAINT IF EXISTS p_payment_outbox_events_status_check;
