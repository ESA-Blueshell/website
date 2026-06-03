-- Split the overloaded observed_at into two unambiguous timestamps:
--   synced_at   - we successfully pushed this member to the external
--                 system (owned by the per-member sync path).
--   verified_at - a reconcile confirmed the member present in a live
--                 remote snapshot (owned by the verifier).
--
-- Drift then reads cleanly: missing = desired row with synced_at NULL;
-- a pushed-but-not-yet-verified member is no longer mistaken for missing.
--
-- observed_at (set on push by the V75 hotfix) becomes verified_at, and
-- synced_at is backfilled from it: anything previously stamped was, by
-- definition, pushed.

ALTER TABLE cohort_member CHANGE COLUMN observed_at verified_at DATETIME(6) NULL;

ALTER TABLE cohort_member ADD COLUMN synced_at DATETIME(6) NULL AFTER external_user_id;

UPDATE cohort_member SET synced_at = verified_at WHERE verified_at IS NOT NULL;

DROP INDEX idx_cohort_member_observed ON cohort_member;
CREATE INDEX idx_cohort_member_verified ON cohort_member (cohort_id, verified_at);
CREATE INDEX idx_cohort_member_synced ON cohort_member (cohort_id, synced_at);
