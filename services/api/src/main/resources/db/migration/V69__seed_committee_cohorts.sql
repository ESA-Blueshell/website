-- Seed one cohort + rule for every active committee so the cohort
-- engine can start tracking committee membership without waiting for
-- the next CommitteeMembershipChanged event.
--
-- Each committee maps to one Cohort(BREVO, LIST, <committee_name>) and
-- one CohortRule(COMMITTEE, <committee_id>, that-cohort). The external
-- Brevo list materializes lazily on first sync via
-- BrevoCohortAdapter.createCohort(), so this migration is local-state-
-- only — no calls fire out to Brevo at migration time.
--
-- Idempotent: re-running on a DB that already has the cohort rows is a
-- no-op thanks to the WHERE NOT EXISTS guards and the unique cohort_rule
-- index.

INSERT INTO cohort (system, kind, label)
SELECT 'BREVO', 'LIST', c.name
FROM committees c
WHERE c.deleted_at = '9999-12-31 23:59:59'
  AND NOT EXISTS (
      SELECT 1
      FROM cohort_rule r
      JOIN cohort co ON co.id = r.cohort_id
      WHERE r.fact_kind = 'COMMITTEE'
        AND r.fact_key = CAST(c.id AS CHAR)
        AND co.system = 'BREVO'
        AND co.kind = 'LIST'
        AND co.deleted_at = '9999-12-31 23:59:59.000000'
  );

INSERT INTO cohort_rule (fact_kind, fact_key, cohort_id, enabled)
SELECT 'COMMITTEE', CAST(c.id AS CHAR), co.id, TRUE
FROM committees c
JOIN cohort co
     ON co.system = 'BREVO'
    AND co.kind = 'LIST'
    AND co.label = c.name
    AND co.deleted_at = '9999-12-31 23:59:59.000000'
WHERE c.deleted_at = '9999-12-31 23:59:59'
  AND NOT EXISTS (
      SELECT 1 FROM cohort_rule r
      WHERE r.fact_kind = 'COMMITTEE'
        AND r.fact_key = CAST(c.id AS CHAR)
        AND r.cohort_id = co.id
  );
