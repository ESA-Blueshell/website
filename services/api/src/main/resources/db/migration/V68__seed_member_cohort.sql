-- Seed the "Members" cohort + its corresponding rule.
--
-- The contribution-period cohorts are seeded lazily by the
-- ContributionPeriodCohortResolver as periods are created, but the
-- "members" cohort has no obvious creation event — every user with
-- Role.MEMBER should be a member of it. Seeding here means the rule
-- engine starts emitting Brevo add/remove calls as soon as the next
-- user re-evaluation runs.
--
-- The external_id_mapping row is intentionally NOT seeded: the
-- BrevoCohortAdapter materialises the Brevo list on the first sync
-- through `createCohort()` and writes the mapping itself, which keeps
-- the Brevo list id out of source control and lets dev / prod each
-- maintain their own.
--
-- Idempotent: re-running on a DB that already has the row is a no-op
-- thanks to the unique cohort_rule index plus the WHERE NOT EXISTS guard.

INSERT INTO cohort (system, kind, label)
SELECT 'BREVO', 'LIST', 'Members'
WHERE NOT EXISTS (
    SELECT 1 FROM cohort
    WHERE system = 'BREVO' AND kind = 'LIST' AND label = 'Members'
      AND deleted_at = '9999-12-31 23:59:59.000000'
);

INSERT INTO cohort_rule (fact_kind, fact_key, cohort_id, enabled)
SELECT 'ROLE', 'MEMBER', c.id, TRUE
FROM cohort c
WHERE c.system = 'BREVO' AND c.kind = 'LIST' AND c.label = 'Members'
  AND c.deleted_at = '9999-12-31 23:59:59.000000'
  AND NOT EXISTS (
      SELECT 1 FROM cohort_rule r
      WHERE r.fact_kind = 'ROLE' AND r.fact_key = 'MEMBER' AND r.cohort_id = c.id
  );
