-- Generic "cohort" abstraction: a named group on one external system
-- (Brevo list, Discord role, Google group, ...) plus per-user membership
-- and an admin-editable rule table mapping a user fact (role, committee
-- membership, contribution-period payment, newsletter opt-in) to a
-- cohort.
--
-- The cohort's system-specific external id (Brevo list id, Discord
-- snowflake, ...) lives in the existing external_id_mapping table with
-- aggregate_type='COHORT' so we inherit string-based snowflake storage
-- and the unified version tracking already used for the per-user identity
-- mapping.
--
-- This migration is additive only: nothing about the existing
-- contact_lists / contact_list_memberships / contact_list_external_ids
-- flow changes here. The cutover from those tables onto the cohort
-- abstraction (and the deletion of ProcessListMembershipJob and the
-- contribution-period special case) is a follow-up PR so each step stays
-- reviewable.

CREATE TABLE cohort (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    system        VARCHAR(32)  NOT NULL,
    kind          VARCHAR(32)  NOT NULL,
    label         VARCHAR(255) NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_id BIGINT       NULL,
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by_id BIGINT       NULL,
    deleted_at    DATETIME(6)  NOT NULL DEFAULT '9999-12-31 23:59:59.000000',
    INDEX idx_cohort_system_kind  (system, kind, deleted_at),
    INDEX idx_cohort_deleted_at   (deleted_at),
    CONSTRAINT fk_cohort_created  FOREIGN KEY (created_by_id) REFERENCES users (id),
    CONSTRAINT fk_cohort_updated  FOREIGN KEY (updated_by_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cohort_member (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    cohort_id     BIGINT       NOT NULL,
    user_id       BIGINT       NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_id BIGINT       NULL,
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by_id BIGINT       NULL,
    deleted_at    DATETIME(6)  NOT NULL DEFAULT '9999-12-31 23:59:59.000000',
    UNIQUE INDEX uk_cohort_member             (cohort_id, user_id, deleted_at),
    INDEX idx_cohort_member_cohort            (cohort_id),
    INDEX idx_cohort_member_user              (user_id),
    INDEX idx_cohort_member_deleted_at        (deleted_at),
    CONSTRAINT fk_cohort_member_cohort  FOREIGN KEY (cohort_id)     REFERENCES cohort (id),
    CONSTRAINT fk_cohort_member_user    FOREIGN KEY (user_id)       REFERENCES users  (id),
    CONSTRAINT fk_cohort_member_created FOREIGN KEY (created_by_id) REFERENCES users  (id),
    CONSTRAINT fk_cohort_member_updated FOREIGN KEY (updated_by_id) REFERENCES users  (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cohort_rule (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    fact_kind   VARCHAR(32)  NOT NULL,
    fact_key    VARCHAR(64)  NOT NULL,
    cohort_id   BIGINT       NOT NULL,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE INDEX uk_cohort_rule          (fact_kind, fact_key, cohort_id),
    INDEX idx_cohort_rule_fact_enabled   (fact_kind, fact_key, enabled),
    INDEX idx_cohort_rule_cohort         (cohort_id),
    CONSTRAINT fk_cohort_rule_cohort FOREIGN KEY (cohort_id) REFERENCES cohort (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
