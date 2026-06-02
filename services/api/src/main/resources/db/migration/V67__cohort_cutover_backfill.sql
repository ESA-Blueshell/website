-- Cutover backfill: copy the legacy contact-list snapshot into the new
-- cohort schema. After this migration runs the cohort tables hold the
-- same source-of-truth state that contact_list* held just before
-- cutover; production code from this PR onward writes only to cohort*.
--
-- The legacy contact_lists / contact_list_memberships /
-- contact_list_external_ids tables are intentionally retained. They
-- become read-only snapshots for verification and rollback. A
-- follow-up migration drops them once the cohort path has been
-- observed running correctly in production.
--
-- IDs are preserved 1:1 between contact_lists.id and cohort.id, and
-- between contact_list_memberships.contact_list_id and
-- cohort_member.cohort_id, so manual side-by-side queries between the
-- two snapshots remain straightforward.

-- ── 1. cohort ← contact_lists
INSERT INTO cohort (
    id, system, kind, label,
    version, created_at, created_by_id, updated_at, updated_by_id, deleted_at
)
SELECT
    id,
    'BREVO', 'LIST', name,
    version, created_at, created_by_id, updated_at, updated_by_id, deleted_at
FROM contact_lists;

-- Bump the cohort id sequence past the highest backfilled id so new
-- rows don't collide. Uses a prepared statement because ALTER TABLE
-- AUTO_INCREMENT does not accept a subquery.
SET @cohort_next_id = (SELECT IFNULL(MAX(id), 0) + 1 FROM cohort);
SET @cohort_seq_sql = CONCAT('ALTER TABLE cohort AUTO_INCREMENT = ', @cohort_next_id);
PREPARE cohort_seq_stmt FROM @cohort_seq_sql;
EXECUTE cohort_seq_stmt;
DEALLOCATE PREPARE cohort_seq_stmt;

-- ── 2. cohort_member ← contact_list_memberships
-- Join via contact_id → user_id (contacts table owns the per-user link).
INSERT INTO cohort_member (
    cohort_id, user_id,
    version, created_at, created_by_id, updated_at, updated_by_id, deleted_at
)
SELECT
    clm.contact_list_id, c.user_id,
    clm.version, clm.created_at, clm.created_by_id, clm.updated_at, clm.updated_by_id, clm.deleted_at
FROM contact_list_memberships clm
JOIN contacts c ON c.id = clm.contact_id;

-- ── 3. external_id_mapping(aggregate_type='COHORT') ← contact_list_external_ids
-- Cohort external ids live in the unified mapping table as strings; the
-- legacy column was BIGINT, so we cast to varchar shape.
INSERT INTO external_id_mapping (
    aggregate_type, aggregate_id, system, external_id, version
)
SELECT
    'COHORT', cle.contact_list_id, cle.system, CAST(cle.external_id AS CHAR), 0
FROM contact_list_external_ids cle;

-- ── 4. cohort_rule (CONTRIBUTION_PAID, <period-id>) per period that has a list
-- This is the load-bearing line of the cutover: it gives every existing
-- contribution-period cohort the rule that keeps the engine's first
-- re-evaluation a no-op rather than a mass remove-from-list storm.
INSERT INTO cohort_rule (
    fact_kind, fact_key, cohort_id, enabled, created_at, updated_at
)
SELECT
    'CONTRIBUTION_PAID', CAST(cp.id AS CHAR), cp.contact_list_id, TRUE,
    CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM contribution_periods cp
WHERE cp.contact_list_id IS NOT NULL
  AND cp.deleted_at = '9999-12-31 23:59:59.000000';

-- ── 5. Mark the legacy tables as snapshots retained for rollback.
ALTER TABLE contact_lists
    COMMENT = 'Retained snapshot for cohort-cutover rollback. Backfilled to cohort table; drop in follow-up migration once verified.';
ALTER TABLE contact_list_memberships
    COMMENT = 'Retained snapshot for cohort-cutover rollback. Backfilled to cohort_member table; drop in follow-up migration once verified.';
ALTER TABLE contact_list_external_ids
    COMMENT = 'Retained snapshot for cohort-cutover rollback. Backfilled to external_id_mapping (aggregate_type=COHORT); drop in follow-up migration once verified.';
