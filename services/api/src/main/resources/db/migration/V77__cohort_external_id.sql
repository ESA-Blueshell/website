-- Move the per-target external id onto the cohort row itself. Until item 8
-- removes the fallback, cohort code reads cohort.external_id first and falls
-- back to the legacy external_id_mapping(aggregate_type='COHORT') row, so a
-- replica running older code keeps resolving during the deploy overlap.
--
-- Preflight (run manually before deploy): CohortTargetIds treats
-- (system, external_id) as a single owner. Confirm the legacy mapping holds no
-- duplicate (system, external_id) for COHORT first — resolve any rows it
-- returns before deploying:
--
--   SELECT system, external_id, COUNT(*) AS cnt
--   FROM external_id_mapping
--   WHERE aggregate_type = 'COHORT' AND external_id IS NOT NULL
--   GROUP BY system, external_id HAVING COUNT(*) > 1;

ALTER TABLE cohort ADD COLUMN external_id VARCHAR(1024) NULL;

UPDATE cohort c
JOIN external_id_mapping m
  ON m.aggregate_type = 'COHORT' AND m.aggregate_id = c.id AND m.system = c.system
SET c.external_id = m.external_id
WHERE c.external_id IS NULL;

-- 191-char prefix keeps the key within the utf8mb4 index-length limit;
-- external_id is far shorter in practice (a Brevo list id).
CREATE INDEX idx_cohort_external_id ON cohort (system, external_id(191), deleted_at);
