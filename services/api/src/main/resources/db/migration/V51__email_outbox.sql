DROP TABLE IF EXISTS email_outbox;
DROP TABLE IF EXISTS emails;

CREATE TABLE emails (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY,
    deleted_at      DATETIME     NOT NULL DEFAULT '9999-12-31 23:59:59',
    recipient_email VARCHAR(255) NOT NULL,
    recipient_name  VARCHAR(255) NOT NULL,
    subject         VARCHAR(512) NOT NULL,
    email_type      VARCHAR(255) NOT NULL,
    delivery_status VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    message_id      VARCHAR(255) NULL,
    tracking_token  VARCHAR(36)  NULL,
    sent_at         DATETIME     NULL,
    delivered_at    DATETIME     NULL,
    opened_at       DATETIME     NULL,
    error_type      VARCHAR(255) NULL,
    error_reason    LONGTEXT     NULL,
    attempts        INT          NOT NULL DEFAULT 0,
    job_execution_id     BIGINT  NULL,
    initiated_by_user_id BIGINT  NULL,
    initiated_by_type    VARCHAR(16) NOT NULL DEFAULT 'SYSTEM',
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by_id BIGINT   NULL,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by_id BIGINT   NULL,
    version       BIGINT   NOT NULL DEFAULT 0
);

CREATE INDEX        idx_emails_status         ON emails (delivery_status);
CREATE INDEX        idx_emails_email_type     ON emails (email_type);
CREATE INDEX        idx_emails_recipient      ON emails (recipient_email);
CREATE INDEX        idx_emails_sent_at        ON emails (sent_at);
CREATE UNIQUE INDEX idx_emails_tracking_token ON emails (tracking_token);
CREATE INDEX        idx_emails_message_id     ON emails (message_id);
