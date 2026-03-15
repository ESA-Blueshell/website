-- ── contacts ──────────────────────────────────────────────────────────────────
CREATE TABLE contacts (
    id                  BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT       NOT NULL,
    synced_email        VARCHAR(255) NOT NULL DEFAULT '',
    synced_first_name   VARCHAR(255) NOT NULL DEFAULT '',
    synced_last_name    VARCHAR(255) NOT NULL DEFAULT '',
    synced_phone_number VARCHAR(50)  NULL,
    synced_newsletter   BOOLEAN      NOT NULL DEFAULT FALSE,
    synced_is_member    BOOLEAN      NOT NULL DEFAULT FALSE,
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_id       BIGINT       NULL,
    updated_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by_id       BIGINT       NULL,
    deleted_at          DATETIME(6)  NOT NULL DEFAULT '9999-12-31 23:59:59.000000',
    UNIQUE INDEX uk_contacts_user_id_deleted_at (user_id, deleted_at),
    INDEX idx_contacts_user_id   (user_id),
    INDEX idx_contacts_deleted_at (deleted_at),
    CONSTRAINT fk_contacts_user    FOREIGN KEY (user_id)       REFERENCES users (id),
    CONSTRAINT fk_contacts_created FOREIGN KEY (created_by_id) REFERENCES users (id),
    CONSTRAINT fk_contacts_updated FOREIGN KEY (updated_by_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── contact_external_ids ──────────────────────────────────────────────────────
CREATE TABLE contact_external_ids (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    contact_id  BIGINT      NOT NULL,
    system      VARCHAR(50) NOT NULL,
    external_id BIGINT      NOT NULL,
    CONSTRAINT uk_contact_external_ids_contact_system UNIQUE (contact_id, system),
    CONSTRAINT fk_contact_external_ids_contact FOREIGN KEY (contact_id) REFERENCES contacts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── contact_lists ─────────────────────────────────────────────────────────────
CREATE TABLE contact_lists (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    folder_name   VARCHAR(100) NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_id BIGINT       NULL,
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by_id BIGINT       NULL,
    deleted_at    DATETIME(6)  NOT NULL DEFAULT '9999-12-31 23:59:59.000000',
    UNIQUE INDEX uk_contact_lists_name_deleted_at (name, deleted_at),
    INDEX idx_contact_lists_deleted_at (deleted_at),
    CONSTRAINT fk_contact_lists_created FOREIGN KEY (created_by_id) REFERENCES users (id),
    CONSTRAINT fk_contact_lists_updated FOREIGN KEY (updated_by_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── contact_list_external_ids ─────────────────────────────────────────────────
CREATE TABLE contact_list_external_ids (
    id              BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    contact_list_id BIGINT      NOT NULL,
    system          VARCHAR(50) NOT NULL,
    external_id     BIGINT      NOT NULL,
    CONSTRAINT uk_contact_list_external_ids_list_system UNIQUE (contact_list_id, system),
    CONSTRAINT fk_contact_list_external_ids_list FOREIGN KEY (contact_list_id) REFERENCES contact_lists (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── contact_list_memberships ──────────────────────────────────────────────────
CREATE TABLE contact_list_memberships (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    contact_id      BIGINT      NOT NULL,
    contact_list_id BIGINT      NOT NULL,
    version         BIGINT      NOT NULL DEFAULT 0,
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_id   BIGINT      NULL,
    updated_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by_id   BIGINT      NULL,
    deleted_at      DATETIME(6) NOT NULL DEFAULT '9999-12-31 23:59:59.000000',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_contact_list_memberships (contact_id, contact_list_id, deleted_at),
    INDEX idx_contact_list_memberships_contact (contact_id),
    INDEX idx_contact_list_memberships_list    (contact_list_id),
    INDEX idx_contact_list_memberships_deleted (deleted_at),
    CONSTRAINT fk_membership_contact FOREIGN KEY (contact_id)      REFERENCES contacts (id),
    CONSTRAINT fk_membership_list    FOREIGN KEY (contact_list_id) REFERENCES contact_lists (id),
    CONSTRAINT fk_membership_created FOREIGN KEY (created_by_id)   REFERENCES users (id),
    CONSTRAINT fk_membership_updated FOREIGN KEY (updated_by_id)   REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── data migration: contacts from users.contact_id (Brevo legacy) ─────────────
INSERT INTO contacts (user_id, synced_email, version, deleted_at)
SELECT id, IFNULL(email, ''), 0, '9999-12-31 23:59:59.000000'
FROM users
WHERE contact_id IS NOT NULL
  AND deleted_at = '9999-12-31 23:59:59.000000';

INSERT INTO contact_external_ids (contact_id, system, external_id)
SELECT cr.id, 'BREVO', u.contact_id
FROM contacts cr
JOIN users u ON u.id = cr.user_id
WHERE u.contact_id IS NOT NULL;

-- ── data migration: contact_lists from contribution_periods (Brevo legacy) ────
INSERT INTO contact_lists (name, version, deleted_at)
SELECT DISTINCT
    CONCAT('Contribution Paid ', YEAR(start_date), ' - ', YEAR(end_date)),
    0,
    '9999-12-31 23:59:59.000000'
FROM contribution_periods
WHERE list_id IS NOT NULL
  AND deleted_at = '9999-12-31 23:59:59.000000';

INSERT INTO contact_list_external_ids (contact_list_id, system, external_id)
SELECT cl.id, 'BREVO', cp.list_id
FROM contact_lists cl
JOIN contribution_periods cp
  ON cl.name = CONCAT('Contribution Paid ', YEAR(cp.start_date), ' - ', YEAR(cp.end_date))
WHERE cp.list_id IS NOT NULL
  AND cp.deleted_at = '9999-12-31 23:59:59.000000';

-- ── contribution_periods: add contact_list_id FK, drop legacy list_id ─────────
ALTER TABLE contribution_periods ADD COLUMN contact_list_id BIGINT NULL;

UPDATE contribution_periods cp
JOIN contact_lists cl
  ON cl.name = CONCAT('Contribution Paid ', YEAR(cp.start_date), ' - ', YEAR(cp.end_date))
  AND cl.deleted_at = '9999-12-31 23:59:59.000000'
SET cp.contact_list_id = cl.id
WHERE cp.list_id IS NOT NULL;

ALTER TABLE contribution_periods DROP INDEX idx_contribution_periods_list_id;
ALTER TABLE contribution_periods DROP COLUMN list_id;

-- ── users: drop legacy contact_id ────────────────────────────────────────────
ALTER TABLE users DROP COLUMN contact_id;
