-- Unified external-id mapping: one row per (aggregate, target system).
-- Replaces (in PR 3) contact_external_ids and events.google_id.
CREATE TABLE external_id_mapping (
    id             BIGINT       AUTO_INCREMENT PRIMARY KEY,
    aggregate_type VARCHAR(64)  NOT NULL,
    aggregate_id   BIGINT       NOT NULL,
    system         VARCHAR(64)  NOT NULL,
    external_id    VARCHAR(255) NULL,
    synced_version BIGINT       NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE INDEX uk_external_id_mapping (aggregate_type, aggregate_id, system),
    INDEX idx_external_id_mapping_system (system, aggregate_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Backfill: contact_external_ids -> USER aggregate (Listmonk/Brevo subscriber ids are integers).
INSERT INTO external_id_mapping (aggregate_type, aggregate_id, system, external_id)
SELECT 'USER', c.user_id, cei.system, CAST(cei.external_id AS CHAR)
FROM contact_external_ids cei
JOIN contacts c ON c.id = cei.contact_id
WHERE c.deleted_at = '9999-12-31 23:59:59.000000';

-- Backfill: contact_list_external_ids -> CONTACT_LIST aggregate.
INSERT INTO external_id_mapping (aggregate_type, aggregate_id, system, external_id)
SELECT 'CONTACT_LIST', cl.id, cle.system, CAST(cle.external_id AS CHAR)
FROM contact_list_external_ids cle
JOIN contact_lists cl ON cl.id = cle.contact_list_id
WHERE cl.deleted_at = '9999-12-31 23:59:59.000000';

-- Backfill: events.google_id -> EVENT aggregate (string ids).
INSERT INTO external_id_mapping (aggregate_type, aggregate_id, system, external_id)
SELECT 'EVENT', e.id, 'GOOGLE_CALENDAR', e.google_id
FROM events e
WHERE e.google_id IS NOT NULL
  AND e.deleted_at = '9999-12-31 23:59:59.000000';
