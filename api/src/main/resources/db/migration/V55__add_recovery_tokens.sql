ALTER TABLE events
    DROP FOREIGN KEY fk_events_creator_id;

ALTER TABLE events
    DROP FOREIGN KEY fk_events_last_editor_id;

ALTER TABLE users
    DROP FOREIGN KEY fk_users_creator_id;

CREATE TABLE recovery_tokens
(
    id            BIGINT AUTO_INCREMENT                  NOT NULL,
    deleted_at    datetime DEFAULT '9999-12-31 23:59:59' NOT NULL,
    created_at    datetime                               NOT NULL,
    created_by_id BIGINT                                 NULL,
    updated_at    datetime                               NOT NULL,
    updated_by_id BIGINT                                 NULL,
    version       BIGINT                                 NOT NULL,
    user_id       BIGINT                                 NOT NULL,
    type          VARCHAR(50)                            NOT NULL,
    selector      VARCHAR(64)                            NOT NULL,
    verifier_hash VARCHAR(255)                           NOT NULL,
    expires_at    datetime                               NOT NULL,
    consumed_at   datetime                               NULL,
    CONSTRAINT pk_recovery_tokens PRIMARY KEY (id)
);

ALTER TABLE recovery_tokens
    ADD CONSTRAINT uk_recovery_selector_deleted_at UNIQUE (selector, deleted_at);

CREATE INDEX idx_contribution_reminders_created_at ON contribution_reminders (created_at);

CREATE INDEX idx_recovery_tokens_expires ON recovery_tokens (expires_at);

CREATE INDEX idx_recovery_tokens_user_id_type_deleted_at ON recovery_tokens (user_id, type, deleted_at);

ALTER TABLE recovery_tokens
    ADD CONSTRAINT FK_RECOVERY_TOKENS_ON_CREATED_BY FOREIGN KEY (created_by_id) REFERENCES users (id);

ALTER TABLE recovery_tokens
    ADD CONSTRAINT FK_RECOVERY_TOKENS_ON_UPDATED_BY FOREIGN KEY (updated_by_id) REFERENCES users (id);

ALTER TABLE recovery_tokens
    ADD CONSTRAINT FK_RECOVERY_TOKENS_ON_USER FOREIGN KEY (user_id) REFERENCES users (id);

UPDATE events
SET created_by_id = creator_id
WHERE creator_id IS NOT NUll;

ALTER TABLE events
    DROP COLUMN creator_id;

UPDATE events
SET updated_by_id = last_editor_id
WHERE last_editor_id IS NOT NULL;

ALTER TABLE events
    DROP COLUMN last_editor_id;

UPDATE users
SET created_by_id = creator_id
WHERE creator_id IS NOT NULL;

ALTER TABLE users
    DROP CONSTRAINT uk_users_reset_key_deleted_at;

ALTER TABLE users
    DROP COLUMN creator_id,
    DROP COLUMN reset_key,
    DROP COLUMN reset_key_valid_until,
    DROP COLUMN reset_type;
