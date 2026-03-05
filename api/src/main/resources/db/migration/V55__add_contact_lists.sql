CREATE TABLE contact_lists (
    id            BIGINT          AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(255)    NOT NULL,
    folder_name   VARCHAR(100),
    version       BIGINT          NOT NULL DEFAULT 0,
    created_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_id BIGINT,
    updated_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by_id BIGINT,
    deleted_at    DATETIME(6)     NOT NULL DEFAULT '9999-12-31 23:59:59.000000',
    UNIQUE INDEX uk_contact_lists_name_deleted_at (name, deleted_at),
    INDEX idx_contact_lists_deleted_at (deleted_at),
    CONSTRAINT fk_contact_lists_created FOREIGN KEY (created_by_id) REFERENCES users (id),
    CONSTRAINT fk_contact_lists_updated FOREIGN KEY (updated_by_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE listmonk_lists (
    list_id     BIGINT  NOT NULL,
    external_id BIGINT  NOT NULL,
    PRIMARY KEY (list_id),
    CONSTRAINT fk_listmonk_lists_list FOREIGN KEY (list_id) REFERENCES contact_lists (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE brevo_lists (
    list_id     BIGINT  NOT NULL,
    external_id BIGINT  NOT NULL,
    PRIMARY KEY (list_id),
    CONSTRAINT fk_brevo_lists_list FOREIGN KEY (list_id) REFERENCES contact_lists (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE contact_list_memberships (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    contact_id BIGINT      NOT NULL,
    contact_list_id   BIGINT      NOT NULL,
    version           BIGINT      NOT NULL DEFAULT 0,
    created_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_id     BIGINT,
    updated_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by_id     BIGINT,
    deleted_at        DATETIME(6) NOT NULL DEFAULT '9999-12-31 23:59:59.000000',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_contact_list_memberships (contact_id, contact_list_id, deleted_at),
    INDEX idx_contact_list_memberships_record (contact_id),
    INDEX idx_contact_list_memberships_list (contact_list_id),
    INDEX idx_contact_list_memberships_deleted_at (deleted_at),
    CONSTRAINT fk_membership_contact FOREIGN KEY (contact_id) REFERENCES contacts (id),
    CONSTRAINT fk_membership_list    FOREIGN KEY (contact_list_id)   REFERENCES contact_lists (id),
    CONSTRAINT fk_membership_created FOREIGN KEY (created_by_id)     REFERENCES users (id),
    CONSTRAINT fk_membership_updated FOREIGN KEY (updated_by_id)     REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
