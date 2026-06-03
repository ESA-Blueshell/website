-- Shadow table: last-observed state of one external system's membership
-- for a cohort mapping. Written by cohort.reconcile-list; read by the
-- drift endpoint. Hard-deletes rows that are no longer present externally.
CREATE TABLE external_cohort_member (
    cohort_id        BIGINT        NOT NULL,
    external_user_id VARCHAR(255)  NOT NULL,
    label            VARCHAR(512)  NULL,
    observed_at      DATETIME(3)   NOT NULL,
    PRIMARY KEY (cohort_id, external_user_id),
    CONSTRAINT fk_ecm_cohort FOREIGN KEY (cohort_id) REFERENCES cohort (id)
);
