SET search_path TO notification_db;

ALTER TABLE p_notifications ALTER COLUMN notification_id DROP DEFAULT;
