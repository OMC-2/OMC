ALTER TABLE p_products          ALTER COLUMN product_id   DROP DEFAULT;
ALTER TABLE p_inventories       ALTER COLUMN inventory_id DROP DEFAULT;
ALTER TABLE p_outbox_events     ALTER COLUMN event_id     DROP DEFAULT;
ALTER TABLE p_failed_event_logs ALTER COLUMN log_id       DROP DEFAULT;