-- Step 1 of retiring cohort_rule: move fact_kind / fact_key / enabled onto
-- cohort_subject. The cohort_rule table is kept for one release (no code reads
-- it after this); step 2 drops it once that release is out.
--
-- Preflight (run manually): the resolver always created exactly one rule per
-- subject, but confirm before adding the unique key:
--
--   SELECT subject_id, COUNT(*) AS rules FROM cohort_rule GROUP BY subject_id HAVING COUNT(*) > 1;

ALTER TABLE cohort_subject
  ADD COLUMN fact_kind VARCHAR(32) NULL,
  ADD COLUMN fact_key  VARCHAR(64) NULL,
  ADD COLUMN enabled   TINYINT(1)  NOT NULL DEFAULT 1;

UPDATE cohort_subject s
JOIN   cohort      c ON c.subject_id = s.id AND c.deleted_at = '9999-12-31 23:59:59'
JOIN   cohort_rule r ON r.cohort_id  = c.id
SET    s.fact_kind = r.fact_kind, s.fact_key = r.fact_key, s.enabled = r.enabled
WHERE  s.fact_key IS NULL;

-- One subject per (fact_kind, fact_key). NULLs do not collide in a MariaDB
-- unique index, so pre-release CUSTOM subjects with no rule are unaffected and
-- an operator can populate them later.
ALTER TABLE cohort_subject ADD UNIQUE KEY uk_cohort_subject_fact (fact_kind, fact_key);
