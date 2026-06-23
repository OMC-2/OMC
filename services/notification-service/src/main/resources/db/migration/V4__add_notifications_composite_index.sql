SET search_path TO notification_db;

CREATE INDEX idx_notifications_user_status ON p_notifications(user_id, status);
