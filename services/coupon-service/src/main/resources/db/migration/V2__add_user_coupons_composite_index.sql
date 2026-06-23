SET search_path TO coupon_db;

CREATE INDEX idx_user_coupons_user_status ON p_user_coupons(user_id, status);
