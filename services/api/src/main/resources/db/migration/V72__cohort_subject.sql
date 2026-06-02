-- Split "logical cohort" (the thing we sync) from "per-target mapping"
-- (where it lands externally).
--
-- Before this migration every `cohort` row was both a logical entity
-- AND a per-target mapping: e.g. `Cohort('Web Cmte', BREVO, LIST)` had
-- the membership rules attached AND the Brevo list id mapped via
-- external_id_mapping. That conflation made it impossible to express
-- "Web Cmte is also a Discord role" without duplicating the rules and
-- the member set.
--
-- The new shape:
--   cohort_subject  → the logical thing (Web Cmte, Members 2025-2026,
--                     Newsletter Subscribers). Owns rules + members.
--   cohort          → unchanged for now; gains a subject_id FK so each
--                     row is a per-system mapping under one subject.
--
-- Phase plan:
--   1. (this migration) add cohort_subject + subject_id FK on
--      cohort/cohort_rule/cohort_member + backfill 1:1.
--   2. (follow-up) refactor the engine to diff subject_member rather
--      than cohort_member; cohort.cohort_id on rules + members becomes
--      vestigial then is dropped.
--
-- Backfill maps each existing cohort to a freshly-minted subject. The
-- subject's `type` is derived from the first rule attached to that
-- cohort (which today is always the only rule), falling back to CUSTOM
-- for ruleless cohorts. Doing this in SQL keeps the migration
-- self-contained — the type-classification table stays next to the
-- column shapes that produce it.

CREATE TABLE cohort_subject (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    type          VARCHAR(32)  NOT NULL,
    label         VARCHAR(255) NOT NULL,
    description   VARCHAR(255) NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_id BIGINT       NULL,
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by_id BIGINT       NULL,
    deleted_at    DATETIME(6)  NOT NULL DEFAULT '9999-12-31 23:59:59.000000',
    INDEX idx_cohort_subject_type        (type, deleted_at),
    INDEX idx_cohort_subject_deleted_at  (deleted_at),
    CONSTRAINT fk_cohort_subject_created FOREIGN KEY (created_by_id) REFERENCES users (id),
    CONSTRAINT fk_cohort_subject_updated FOREIGN KEY (updated_by_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE cohort        ADD COLUMN subject_id BIGINT NULL AFTER folder;
ALTER TABLE cohort_rule   ADD COLUMN subject_id BIGINT NULL AFTER cohort_id;
ALTER TABLE cohort_member ADD COLUMN subject_id BIGINT NULL AFTER cohort_id;

-- One subject per existing active cohort. Type derived from the
-- cohort's first (and today only) rule's fact_kind.
INSERT INTO cohort_subject (type, label)
SELECT
    COALESCE(
        (
            SELECT CASE r.fact_kind
                       WHEN 'COMMITTEE'         THEN 'COMMITTEE_MEMBERS'
                       WHEN 'CONTRIBUTION_PAID' THEN 'PERIOD_PAYERS'
                       WHEN 'MEMBER_IN_PERIOD'  THEN 'PERIOD_MEMBERS'
                       WHEN 'ACTIVE_IN_PERIOD'  THEN 'PERIOD_ACTIVE_MEMBERS'
                       WHEN 'NEWSLETTER'        THEN 'NEWSLETTER_SUBSCRIBERS'
                       ELSE 'CUSTOM'
                   END
            FROM cohort_rule r
            WHERE r.cohort_id = c.id
            ORDER BY r.id
            LIMIT 1
        ),
        'CUSTOM'
    ),
    c.label
FROM cohort c
WHERE c.deleted_at = '9999-12-31 23:59:59.000000';

-- Pair each cohort with its freshly-inserted subject by row position
-- inside the deterministic ORDER BY id we used for the INSERT.
UPDATE cohort c
JOIN (
    SELECT c.id AS cohort_id, s.id AS subject_id
    FROM (
        SELECT id, label, ROW_NUMBER() OVER (ORDER BY id) AS rn
        FROM cohort
        WHERE deleted_at = '9999-12-31 23:59:59.000000'
    ) c
    JOIN (
        SELECT id, label, ROW_NUMBER() OVER (ORDER BY id) AS rn
        FROM cohort_subject
    ) s ON c.rn = s.rn AND c.label = s.label
) pair ON pair.cohort_id = c.id
SET c.subject_id = pair.subject_id;

UPDATE cohort_rule r
JOIN cohort c ON c.id = r.cohort_id
SET r.subject_id = c.subject_id
WHERE r.subject_id IS NULL;

UPDATE cohort_member m
JOIN cohort c ON c.id = m.cohort_id
SET m.subject_id = c.subject_id
WHERE m.subject_id IS NULL;

ALTER TABLE cohort_rule   MODIFY COLUMN subject_id BIGINT NOT NULL;
ALTER TABLE cohort_member MODIFY COLUMN subject_id BIGINT NOT NULL;

ALTER TABLE cohort
    ADD CONSTRAINT fk_cohort_subject
    FOREIGN KEY (subject_id) REFERENCES cohort_subject (id);

ALTER TABLE cohort_rule
    ADD CONSTRAINT fk_cohort_rule_subject
    FOREIGN KEY (subject_id) REFERENCES cohort_subject (id) ON DELETE CASCADE;

ALTER TABLE cohort_member
    ADD CONSTRAINT fk_cohort_member_subject
    FOREIGN KEY (subject_id) REFERENCES cohort_subject (id) ON DELETE CASCADE;

CREATE INDEX idx_cohort_rule_subject   ON cohort_rule   (subject_id);
CREATE INDEX idx_cohort_member_subject ON cohort_member (subject_id);
CREATE INDEX idx_cohort_subject_fk     ON cohort        (subject_id);
