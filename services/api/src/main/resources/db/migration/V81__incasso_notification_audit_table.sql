-- Create incasso_notifications table for bulk incasso notification audit trail
CREATE TABLE incasso_notifications (
    user_id BIGINT NOT NULL,
    contribution_period_id BIGINT NOT NULL,
    amount DOUBLE PRECISION,
    expected_incasso_date DATE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP(6) NOT NULL DEFAULT '9999-12-31 23:59:59',
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, contribution_period_id),
    CONSTRAINT fk_incasso_notifications_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_incasso_notifications_period FOREIGN KEY (contribution_period_id) REFERENCES contribution_periods(id),
    UNIQUE KEY uk_incasso_notifications_user_period_deleted_at (user_id, contribution_period_id, deleted_at),
    INDEX idx_incasso_notifications_deleted_at (deleted_at),
    INDEX idx_incasso_notifications_created_at (created_at),
    INDEX idx_incasso_notifications_user_id (user_id, deleted_at),
    INDEX idx_incasso_notifications_contribution_period_id (contribution_period_id, deleted_at)
);
