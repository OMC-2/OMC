-- slack_id는 Slack을 등록하지 않은 사용자도 알림을 받을 수 있어야 하므로 nullable로 변경
ALTER TABLE notification_db.p_notifications
    ALTER COLUMN slack_id DROP NOT NULL;
