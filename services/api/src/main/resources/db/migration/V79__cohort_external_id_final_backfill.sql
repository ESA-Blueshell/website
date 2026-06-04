-- Final backfill of cohort.external_id from the legacy
-- external_id_mapping(aggregate_type='COHORT') rows, closing the V77 deploy
-- window: this release stops reading that mapping (CohortTargetIds.find no
-- longer falls back to it), so any cohort an older replica materialised during
-- the overlap — column NULL, mapping set — is copied across one last time.
--
-- Idempotent: only fills rows still missing the column. The legacy COHORT
-- mapping rows are left in place and dropped in a later migration.

UPDATE cohort c
JOIN external_id_mapping m
  ON m.aggregate_type = 'COHORT' AND m.aggregate_id = c.id AND m.system = c.system
SET c.external_id = m.external_id
WHERE c.external_id IS NULL;
