CREATE TABLE contribution_reminders
(
    id                     BIGINT AUTO_INCREMENT                  NOT NULL,
    user_id                BIGINT                                 NOT NULL,
    contribution_period_id BIGINT                                 NOT NULL,
    deleted_at             datetime DEFAULT '9999-12-31 23:59:59' NOT NULL,
    created_at             datetime DEFAULT NOW()                 NOT NULL,
    CONSTRAINT pk_contribution_reminders PRIMARY KEY (id)
);

ALTER TABLE contribution_reminders
    ADD CONSTRAINT uk_contribution_reminders_user_period_deleted_at UNIQUE (user_id, contribution_period_id, deleted_at);

CREATE INDEX idx_contribution_reminders_deleted_at ON contribution_reminders (deleted_at);

ALTER TABLE contribution_reminders
    ADD CONSTRAINT FK_CONTRIBUTION_REMINDERS_ON_CONTRIBUTION_PERIOD FOREIGN KEY (contribution_period_id) REFERENCES contribution_periods (id);

CREATE INDEX idx_contribution_reminders_contribution_period_id ON contribution_reminders (contribution_period_id);

ALTER TABLE contribution_reminders
    ADD CONSTRAINT FK_CONTRIBUTION_REMINDERS_ON_USER FOREIGN KEY (user_id) REFERENCES users (id);

CREATE INDEX idx_contribution_reminders_user_id ON contribution_reminders (user_id);