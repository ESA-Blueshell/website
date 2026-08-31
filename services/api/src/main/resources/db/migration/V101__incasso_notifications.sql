-- The direct-debit pre-notification, recorded separately from the payment request.
--
-- Two tables rather than one with a kind column: the treasurer's question is which
-- statement a member received, and a member can receive both in the same period if their
-- direct-debit flag changed part way through it.
--
-- One row per notification, not per member and period: a debit date that moves has to be
-- re-notified, and each telling is a thing that happened.
CREATE TABLE incasso_notifications
(
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id                BIGINT                                 NOT NULL,
    contribution_period_id BIGINT                                 NOT NULL,
    fee_type               VARCHAR(32)                            NOT NULL,
    amount                 DOUBLE                                 NOT NULL,
    debit_date             DATE                                   NOT NULL,
    asked_at               datetime                               NOT NULL,
    deleted_at             datetime DEFAULT '9999-12-31 23:59:59' NOT NULL,
    created_at             datetime DEFAULT CURRENT_TIMESTAMP     NOT NULL,
    updated_at             datetime DEFAULT CURRENT_TIMESTAMP     NOT NULL,
    version                BIGINT   DEFAULT 0                     NOT NULL,
    created_by_id          BIGINT                                 NULL,
    updated_by_id          BIGINT                                 NULL
);

ALTER TABLE incasso_notifications
    ADD CONSTRAINT fk_incasso_notifications_user_id
        FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE incasso_notifications
    ADD CONSTRAINT fk_incasso_notifications_contribution_period_id
        FOREIGN KEY (contribution_period_id) REFERENCES contribution_periods (id);

ALTER TABLE incasso_notifications
    ADD CONSTRAINT fk_incasso_notifications_created_by_id
        FOREIGN KEY (created_by_id) REFERENCES users (id);

ALTER TABLE incasso_notifications
    ADD CONSTRAINT fk_incasso_notifications_updated_by_id
        FOREIGN KEY (updated_by_id) REFERENCES users (id);

CREATE INDEX idx_incasso_notifications_deleted_at ON incasso_notifications (deleted_at);
CREATE INDEX idx_incasso_notifications_created_at ON incasso_notifications (created_at);
CREATE INDEX idx_incasso_notifications_user_id ON incasso_notifications (user_id, deleted_at);
CREATE INDEX idx_incasso_notifications_contribution_period_id
    ON incasso_notifications (contribution_period_id, deleted_at);
CREATE INDEX idx_incasso_notifications_user_period_asked
    ON incasso_notifications (user_id, contribution_period_id, asked_at);
