ALTER TABLE email_outbox
    ADD COLUMN tracking_token VARCHAR(36) NULL AFTER message_id;

CREATE UNIQUE INDEX idx_email_outbox_tracking_token ON email_outbox (tracking_token);
