ALTER TABLE emails
    ADD COLUMN tracking_token VARCHAR(36) NULL AFTER message_id;

CREATE UNIQUE INDEX idx_emails_tracking_token ON emails (tracking_token);
