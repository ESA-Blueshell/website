-- Add a `folder` column on cohort so the admin UI can group cohorts
-- semantically: per-committee cohorts under "Committees", per-period
-- cohorts under "Periods", and so on. The column carries the canonical
-- display name; per-target adapters translate it into a vendor folder
-- id (Brevo folder id, Discord category, …) when materialising the
-- external counterpart.
--
-- Backfill the rows that V67 and V69 seeded:
--   - any cohort referenced by a (COMMITTEE, _) rule → folder = 'Committees'
--   - any cohort referenced by a (CONTRIBUTION_PAID|MEMBER_IN_PERIOD|
--     ACTIVE_IN_PERIOD, _) rule → folder = 'Periods'
-- Cohorts without a rule (or with rules we have not classified yet, e.g.
-- NEWSLETTER) keep folder = NULL and the dashboard renders them under an
-- "Other" group.

ALTER TABLE cohort
    ADD COLUMN folder VARCHAR(64) DEFAULT NULL AFTER label;

CREATE INDEX idx_cohort_folder ON cohort (folder);

UPDATE cohort c
JOIN (
    SELECT DISTINCT cohort_id
    FROM cohort_rule
    WHERE fact_kind = 'COMMITTEE'
) r ON r.cohort_id = c.id
SET c.folder = 'Committees'
WHERE c.deleted_at = '9999-12-31 23:59:59.000000'
  AND c.folder IS NULL;

UPDATE cohort c
JOIN (
    SELECT DISTINCT cohort_id
    FROM cohort_rule
    WHERE fact_kind IN ('CONTRIBUTION_PAID', 'MEMBER_IN_PERIOD', 'ACTIVE_IN_PERIOD')
) r ON r.cohort_id = c.id
SET c.folder = 'Periods'
WHERE c.deleted_at = '9999-12-31 23:59:59.000000'
  AND c.folder IS NULL;
