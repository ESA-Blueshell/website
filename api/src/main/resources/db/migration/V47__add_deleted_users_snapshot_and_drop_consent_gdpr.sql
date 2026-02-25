-- Add deleted_users snapshot table for soft-delete restore flow
-- and remove unused consent_gdpr column from users.

DROP TABLE IF EXISTS deleted_users;

CREATE TABLE deleted_users
(
    user_id          BIGINT       NOT NULL,
    username         VARCHAR(255) NOT NULL,
    email            VARCHAR(255) NOT NULL,
    initials         VARCHAR(255) NOT NULL,
    first_name       VARCHAR(255) NOT NULL,
    prefix           VARCHAR(255) NULL,
    last_name        VARCHAR(255) NOT NULL,
    phone_number     VARCHAR(255) NULL,
    discord          VARCHAR(255) NULL,
    newsletter       BIT(1)       NOT NULL,
    enabled          BIT(1)       NOT NULL,
    address_id       BIGINT       NULL,
    deleted_at       DATETIME     NOT NULL,
    restore_until_at DATETIME     NOT NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version          BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_deleted_users PRIMARY KEY (user_id),
    CONSTRAINT fk_deleted_users_user_id FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_deleted_users_restore_until_at
    ON deleted_users (restore_until_at);

CREATE INDEX idx_deleted_users_deleted_at
    ON deleted_users (deleted_at);

CREATE INDEX idx_deleted_users_address_id
    ON deleted_users (address_id);

-- Remove unused consent_gdpr column
ALTER TABLE users DROP COLUMN consent_gdpr;
