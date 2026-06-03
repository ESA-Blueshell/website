-- V74 added cohort_member.observed_at as a nullable column with no
-- backfill. The drift read classifies a desired row (user_id set) with
-- observed_at NULL as "missing / not synced", so every member carried
-- over already-synced by the V67 cutover instantly read as missing.
--
-- Stamp observed_at for active desired rows so the panel reflects
-- reality. updated_at is a conservative observed timestamp. A member who
-- is genuinely absent externally is self-healed on the next reconcile:
-- it clears observed_at and re-enqueues an ADD (which now stamps on
-- success). Stranger rows (user_id IS NULL) are left untouched.
--
-- The sentinel '9999-12-31 23:59:59' matches CohortMember's @SQLRestriction
-- (active row marker).
UPDATE cohort_member
   SET observed_at = updated_at
 WHERE user_id IS NOT NULL
   AND observed_at IS NULL
   AND deleted_at = '9999-12-31 23:59:59';
