-- NULLIF(col, ''): 빈 문자열('')은 UUID로 직접 캐스팅할 수 없으므로 NULL로 변환 후 캐스팅한다.
ALTER TABLE p_drops
    ALTER COLUMN created_by TYPE UUID USING NULLIF(created_by, '')::UUID,
    ALTER COLUMN updated_by TYPE UUID USING NULLIF(updated_by, '')::UUID,
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN deleted_by UUID;
