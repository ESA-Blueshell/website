-- A cohort record now says which definition in code produces it.
--
-- The rule used to be two columns: a fact kind and a free-form key, matched against facts
-- derived per user. Every cohort type pinned exactly one fact kind, so the two enumerations
-- were one list written twice, and reading what a cohort meant took three files. The rule
-- lives in code now, and the record's job is to hold the link to the external target and the
-- membership ledger — so all it needs from the rule is its name.
--
-- The key is backfilled from what the row already says: the type it is, and the fact key that
-- named the period or committee it is about. Nothing is dropped that cannot be reconstructed
-- from the definitions themselves.

ALTER TABLE cohort_subject
    ADD COLUMN definition_key VARCHAR(64) NULL AFTER type;

-- `TYPE:scope` for the types that fan out, and the bare type for the one that does not.
UPDATE cohort_subject
SET definition_key = CASE
        WHEN type = 'NEWSLETTER_SUBSCRIBERS' THEN 'NEWSLETTER_SUBSCRIBERS'
        ELSE CONCAT(type, ':', fact_key)
    END
WHERE definition_key IS NULL
  AND deleted_at = '9999-12-31 23:59:59.000000';

-- Soft-deleted rows keep a null key: they name no definition, and the unique index below
-- counts them out the same way it counts out a second deletion of the same cohort.
CREATE UNIQUE INDEX uk_cohort_subject_definition ON cohort_subject (definition_key, deleted_at);

-- `enabled` goes with the rule it belonged to. A cohort exists because a definition exists,
-- and switching one off is a change to the definitions rather than a flag nothing reads: no
-- surface ever offered the toggle, it was only ever displayed.
ALTER TABLE cohort_subject
    DROP INDEX uk_cohort_subject_fact;

ALTER TABLE cohort_subject
    DROP COLUMN fact_kind,
    DROP COLUMN fact_key,
    DROP COLUMN enabled;
