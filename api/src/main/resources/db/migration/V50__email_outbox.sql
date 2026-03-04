CREATE TABLE email_outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    deleted_at DATETIME DEFAULT '9999-12-31 23:59:59' NOT NULL,
    recipient_email VARCHAR(255) NOT NULL,
    recipient_name  VARCHAR(255) NOT NULL,
    subject         VARCHAR(512) NOT NULL,
    email_type      VARCHAR(255) NOT NULL,
    delivery_status VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    message_id      VARCHAR(255) NULL,
    sent_at         DATETIME NULL,
    delivered_at    DATETIME NULL,
    opened_at       DATETIME NULL,
    error_type      VARCHAR(255) NULL,
    error_reason    LONGTEXT NULL,
    attempts        INT NOT NULL DEFAULT 0,
    job_execution_id       BIGINT NULL,
    initiated_by_user_id   BIGINT NULL,
    initiated_by_type      VARCHAR(16) NOT NULL DEFAULT 'SYSTEM',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by_id BIGINT NULL,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by_id BIGINT NULL,
    version     BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_email_outbox_status     ON email_outbox (delivery_status);
CREATE INDEX idx_email_outbox_email_type ON email_outbox (email_type);
CREATE INDEX idx_email_outbox_recipient  ON email_outbox (recipient_email);
CREATE INDEX idx_email_outbox_sent_at    ON email_outbox (sent_at);
