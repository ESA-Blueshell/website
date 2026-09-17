-- Who changed what a person may reach, and when.
--
-- The roles a person holds are decided in four places — the entity default, a membership, a
-- committee seat and a hand-grant — and only a hand-grant is a decision somebody made. This
-- table records those, so "who made them an admin?" has an answer months later. The listeners
-- that keep the derived roles in step are not recorded here: a membership and a committee seat
-- are records in their own right.
--
-- Both sides of the change are stored whole rather than as a delta, because the endpoint states
-- the intended end state and the reader's question is what the person held before and after.
-- The roles are a comma-separated list of enum names, ordered by the enum: a set small enough
-- that a second table would buy a join and nothing else.
CREATE TABLE role_changes
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    subject_user_id BIGINT                                 NOT NULL,
    actor_user_id   BIGINT                                 NOT NULL,
    roles_before    VARCHAR(255)                           NOT NULL,
    roles_after     VARCHAR(255)                           NOT NULL,
    note            VARCHAR(1023)                          NULL,
    changed_at      datetime                               NOT NULL,
    deleted_at      datetime DEFAULT '9999-12-31 23:59:59' NOT NULL,
    created_at      datetime DEFAULT CURRENT_TIMESTAMP     NOT NULL,
    updated_at      datetime DEFAULT CURRENT_TIMESTAMP     NOT NULL,
    version         BIGINT   DEFAULT 0                     NOT NULL,
    created_by_id   BIGINT                                 NULL,
    updated_by_id   BIGINT                                 NULL
);

ALTER TABLE role_changes
    ADD CONSTRAINT fk_role_changes_subject_user_id
        FOREIGN KEY (subject_user_id) REFERENCES users (id);

ALTER TABLE role_changes
    ADD CONSTRAINT fk_role_changes_actor_user_id
        FOREIGN KEY (actor_user_id) REFERENCES users (id);

ALTER TABLE role_changes
    ADD CONSTRAINT fk_role_changes_created_by_id
        FOREIGN KEY (created_by_id) REFERENCES users (id);

ALTER TABLE role_changes
    ADD CONSTRAINT fk_role_changes_updated_by_id
        FOREIGN KEY (updated_by_id) REFERENCES users (id);

CREATE INDEX idx_role_changes_deleted_at ON role_changes (deleted_at);
-- The history is read per person, newest first.
CREATE INDEX idx_role_changes_subject_changed_at ON role_changes (subject_user_id, changed_at);
