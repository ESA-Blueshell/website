CREATE TABLE contacts (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT          NOT NULL,
    synced_email        VARCHAR(255)    NOT NULL DEFAULT '',
    synced_first_name   VARCHAR(255)    NOT NULL DEFAULT '',
    synced_last_name    VARCHAR(255)    NOT NULL DEFAULT '',
    synced_phone_number VARCHAR(50),
    synced_newsletter   BOOLEAN         NOT NULL DEFAULT FALSE,
    synced_is_member    BOOLEAN         NOT NULL DEFAULT FALSE,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_id       BIGINT,
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by_id       BIGINT,
    deleted_at          DATETIME(6)     NOT NULL DEFAULT '9999-12-31 23:59:59.000000',
    UNIQUE INDEX uk_contacts_user_id_deleted_at (user_id, deleted_at),
    INDEX idx_contacts_user_id (user_id),
    INDEX idx_contacts_deleted_at (deleted_at),
    CONSTRAINT fk_contacts_user    FOREIGN KEY (user_id)       REFERENCES users (id),
    CONSTRAINT fk_contacts_created FOREIGN KEY (created_by_id) REFERENCES users (id),
    CONSTRAINT fk_contacts_updated FOREIGN KEY (updated_by_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE listmonk_contacts (
    contact_id  BIGINT  NOT NULL,
    external_id BIGINT  NOT NULL,
    PRIMARY KEY (contact_id),
    CONSTRAINT fk_listmonk_contacts_record FOREIGN KEY (contact_id) REFERENCES contacts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE brevo_contacts (
    contact_id  BIGINT  NOT NULL,
    external_id BIGINT  NOT NULL,
    PRIMARY KEY (contact_id),
    CONSTRAINT fk_brevo_contacts_record FOREIGN KEY (contact_id) REFERENCES contacts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
