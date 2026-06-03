-- Extend cohort_member into a unified ledger for both desired members
-- (user_id set) and remote-only observed members (user_id null,
-- external_user_id + observed_at set).
--
-- Desired row:  user_id IS NOT NULL, observed_at IS NULL  → not yet synced
-- Healthy row:  user_id IS NOT NULL, observed_at IS NOT NULL → confirmed
-- Stranger row: user_id IS NULL,     observed_at IS NOT NULL → extra externally
--
-- MariaDB allows multiple NULLs in a unique index column, so
-- uk_cohort_member (cohort_id, user_id, deleted_at) continues to
-- prevent duplicate desired rows while allowing multiple stranger rows
-- in the same cohort.

ALTER TABLE cohort_member MODIFY user_id BIGINT NULL;

ALTER TABLE cohort_member
    ADD COLUMN external_user_id VARCHAR(255) NULL AFTER user_id,
    ADD COLUMN observed_at      DATETIME(6)  NULL AFTER external_user_id,
    ADD COLUMN label            VARCHAR(512) NULL AFTER observed_at;

ALTER TABLE cohort_member
    ADD CONSTRAINT uk_cohort_member_external
    UNIQUE (cohort_id, external_user_id, deleted_at);

CREATE INDEX idx_cohort_member_external
    ON cohort_member (cohort_id, external_user_id);

CREATE INDEX idx_cohort_member_observed
    ON cohort_member (cohort_id, observed_at);
