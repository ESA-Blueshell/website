-- Seed the "Newsletter Subscribers" cohort + its rule.
--
-- Mirrors V68 for ROLE=MEMBER: a single rule pivoting on the
-- (NEWSLETTER, "true") fact emitted by UserFactCollector whenever
-- User.newsletter is true. Opt-out users are simply absent from the
-- cohort — UserFact does not emit a "false" fact, so removing the
-- opt-in flips the engine into a remove call on next evaluation.
--
-- External Brevo list materialises lazily on first sync.

INSERT INTO cohort (system, kind, label)
SELECT 'BREVO', 'LIST', 'Newsletter Subscribers'
WHERE NOT EXISTS (
    SELECT 1 FROM cohort
    WHERE system = 'BREVO' AND kind = 'LIST' AND label = 'Newsletter Subscribers'
      AND deleted_at = '9999-12-31 23:59:59.000000'
);

INSERT INTO cohort_rule (fact_kind, fact_key, cohort_id, enabled)
SELECT 'NEWSLETTER', 'true', c.id, TRUE
FROM cohort c
WHERE c.system = 'BREVO' AND c.kind = 'LIST' AND c.label = 'Newsletter Subscribers'
  AND c.deleted_at = '9999-12-31 23:59:59.000000'
  AND NOT EXISTS (
      SELECT 1 FROM cohort_rule r
      WHERE r.fact_kind = 'NEWSLETTER' AND r.fact_key = 'true' AND r.cohort_id = c.id
  );
