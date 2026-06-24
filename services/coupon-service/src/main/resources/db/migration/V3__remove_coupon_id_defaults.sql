SET search_path TO coupon_db;

ALTER TABLE p_coupons       ALTER COLUMN coupon_id      DROP DEFAULT;
ALTER TABLE p_user_coupons  ALTER COLUMN user_coupon_id DROP DEFAULT;
ALTER TABLE p_coupon_outbox ALTER COLUMN event_id       DROP DEFAULT;
