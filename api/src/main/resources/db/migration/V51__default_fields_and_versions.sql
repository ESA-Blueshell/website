CREATE TABLE contribution_reminders
(
    id                     BIGINT AUTO_INCREMENT                  NOT NULL,
    deleted_at             datetime DEFAULT '9999-12-31 23:59:59' NOT NULL,
    created_at             datetime                               NOT NULL,
    updated_at             datetime                               NOT NULL,
    version                BIGINT                                 NOT NULL,
    user_id                BIGINT                                 NOT NULL,
    contribution_period_id BIGINT                                 NOT NULL,
    CONSTRAINT pk_contribution_reminders PRIMARY KEY (id)
);

ALTER TABLE addresses
    ADD updated_at datetime NULL;

ALTER TABLE answers
    ADD updated_at datetime NULL;

ALTER TABLE blogs
    ADD updated_at datetime NULL;

ALTER TABLE board_documents
    ADD updated_at datetime NULL;

ALTER TABLE board_members
    ADD updated_at datetime NULL;

ALTER TABLE boards
    ADD updated_at datetime NULL;

ALTER TABLE committee_members
    ADD updated_at datetime NULL;

ALTER TABLE committees
    ADD updated_at datetime NULL;

ALTER TABLE contributions
    ADD updated_at datetime NULL;

ALTER TABLE contribution_periods
    ADD updated_at datetime NULL;

ALTER TABLE event_banners
    ADD updated_at datetime NULL;

ALTER TABLE event_feedback
    ADD updated_at datetime NULL;

ALTER TABLE event_pictures
    ADD updated_at datetime NULL;

ALTER TABLE event_sign_up_answers
    ADD updated_at datetime NULL;

ALTER TABLE event_signups
    ADD updated_at datetime NULL;

ALTER TABLE events
    ADD updated_at datetime NULL;

ALTER TABLE files
    ADD updated_at datetime NULL;

ALTER TABLE guests
    ADD updated_at datetime NULL;

ALTER TABLE memberships
    ADD updated_at datetime NULL;

ALTER TABLE questions
    ADD updated_at datetime NULL;

ALTER TABLE redirects
    ADD updated_at datetime NULL;

ALTER TABLE sponsors
    ADD updated_at datetime NULL;

ALTER TABLE surveys
    ADD updated_at datetime NULL;
ALTER TABLE telemetries
    ADD updated_at datetime NULL;

ALTER TABLE users
    ADD updated_at datetime NULL;

UPDATE `boards`
SET updated_at = COALESCE(updated_at, created_at, NOW())
WHERE updated_at IS NULL;

UPDATE `event_banners`
SET updated_at = COALESCE(updated_at, created_at, NOW())
WHERE updated_at IS NULL;

UPDATE `events`
SET updated_at = COALESCE(updated_at, created_at, NOW())
WHERE updated_at IS NULL;

UPDATE `users`
SET updated_at = COALESCE(updated_at, created_at, NOW())
WHERE updated_at IS NULL;

UPDATE `event_signups`
SET updated_at = COALESCE(updated_at, created_at, NOW())
WHERE updated_at IS NULL;

UPDATE `event_feedback`
SET updated_at = COALESCE(updated_at, created_at, NOW())
WHERE updated_at IS NULL;

UPDATE `board_documents`
SET updated_at = COALESCE(updated_at, created_at, NOW())
WHERE updated_at IS NULL;

UPDATE `contributions`
SET updated_at = COALESCE(updated_at, created_at, NOW())
WHERE updated_at IS NULL;

UPDATE `contribution_periods`
SET updated_at = COALESCE(updated_at, created_at, NOW())
WHERE updated_at IS NULL;

UPDATE `event_sign_up_answers`
SET updated_at = COALESCE(updated_at, created_at, NOW())
WHERE updated_at IS NULL;

UPDATE `memberships`
SET updated_at = COALESCE(updated_at, created_at, NOW())
WHERE updated_at IS NULL;

UPDATE `files`
SET updated_at = COALESCE(updated_at, created_at, NOW())
WHERE updated_at IS NULL;

UPDATE `sponsors`
SET updated_at = COALESCE(updated_at, created_at, NOW())
WHERE updated_at IS NULL;

UPDATE `board_members`
SET updated_at = COALESCE(updated_at, created_at, NOW())
WHERE updated_at IS NULL;

UPDATE `addresses`
SET updated_at = COALESCE(updated_at, created_at, NOW())
WHERE updated_at IS NULL;

UPDATE `surveys`
SET updated_at = COALESCE(updated_at, created_at, NOW())
WHERE updated_at IS NULL;

UPDATE `event_pictures`
SET updated_at = COALESCE(updated_at, created_at, NOW())
WHERE updated_at IS NULL;

UPDATE `committee_members`
SET updated_at = COALESCE(updated_at, created_at, NOW())
WHERE updated_at IS NULL;

UPDATE `contribution_reminders`
SET updated_at = COALESCE(updated_at, created_at, NOW());

UPDATE `telemetries`
SET updated_at = COALESCE(updated_at, created_at, NOW())
WHERE updated_at IS NULL;

UPDATE `guests`
SET updated_at = COALESCE(updated_at, created_at, NOW())
WHERE updated_at IS NULL;

UPDATE `committees`
SET updated_at = COALESCE(updated_at, created_at, NOW())
WHERE updated_at IS NULL;

UPDATE `blogs`
SET updated_at = COALESCE(updated_at, created_at, NOW())
WHERE updated_at IS NULL;

UPDATE `redirects`
SET updated_at = COALESCE(updated_at, created_at, NOW())
WHERE updated_at IS NULL;

UPDATE `answers`
SET updated_at = COALESCE(updated_at, created_at, NOW())
WHERE updated_at IS NULL;

UPDATE `questions`
SET updated_at = COALESCE(updated_at, created_at, NOW())
WHERE updated_at IS NULL;

ALTER TABLE addresses
    ADD version BIGINT NULL;

ALTER TABLE addresses
    MODIFY updated_at datetime NOT NULL;

ALTER TABLE answers
    ADD version BIGINT NULL;

ALTER TABLE answers
    MODIFY updated_at datetime NOT NULL;

ALTER TABLE blogs
    ADD version BIGINT NULL;

ALTER TABLE blogs
    MODIFY updated_at datetime NOT NULL;

ALTER TABLE board_documents
    ADD version BIGINT NULL;

ALTER TABLE board_documents
    MODIFY updated_at datetime NOT NULL;

ALTER TABLE board_members
    ADD version BIGINT NULL;

ALTER TABLE board_members
    MODIFY updated_at datetime NOT NULL;

ALTER TABLE boards
    ADD version BIGINT NULL;

ALTER TABLE boards
    MODIFY updated_at datetime NOT NULL;

ALTER TABLE committee_members
    ADD version BIGINT NULL;

ALTER TABLE committee_members
    MODIFY updated_at datetime NOT NULL;

ALTER TABLE committees
    ADD version BIGINT NULL;

ALTER TABLE committees
    MODIFY updated_at datetime NOT NULL;

ALTER TABLE contribution_periods
    ADD version BIGINT NULL;

ALTER TABLE contribution_periods
    MODIFY updated_at datetime NOT NULL;

ALTER TABLE contributions
    MODIFY updated_at datetime NOT NULL;

ALTER TABLE event_banners
    MODIFY updated_at datetime NOT NULL;

ALTER TABLE contributions
    ADD version BIGINT NULL;

ALTER TABLE event_banners
    ADD version BIGINT NULL;

ALTER TABLE event_feedback
    ADD version BIGINT NULL;

ALTER TABLE event_pictures
    ADD version BIGINT NULL;

ALTER TABLE event_sign_up_answers
    ADD version BIGINT NULL;

ALTER TABLE event_signups
    ADD version BIGINT NULL;

ALTER TABLE events
    ADD version BIGINT NULL;

ALTER TABLE files
    ADD version BIGINT NULL;

ALTER TABLE memberships
    ADD version BIGINT NULL;

ALTER TABLE questions
    ADD version BIGINT NULL;

ALTER TABLE redirects
    ADD version BIGINT NULL;

ALTER TABLE event_feedback
    MODIFY updated_at datetime NOT NULL;

ALTER TABLE event_pictures
    MODIFY updated_at datetime NOT NULL;

ALTER TABLE event_sign_up_answers
    MODIFY updated_at datetime NOT NULL;

ALTER TABLE event_signups
    MODIFY updated_at datetime NOT NULL;

ALTER TABLE guests
    ADD version BIGINT NULL;

ALTER TABLE events
    MODIFY updated_at datetime NOT NULL;

ALTER TABLE files
    MODIFY updated_at datetime NOT NULL;

ALTER TABLE guests
    MODIFY updated_at datetime NOT NULL;

ALTER TABLE memberships
    MODIFY updated_at datetime NOT NULL;

ALTER TABLE questions
    MODIFY updated_at datetime NOT NULL;

ALTER TABLE redirects
    MODIFY updated_at datetime NOT NULL;

ALTER TABLE sponsors
    ADD version BIGINT NULL;

ALTER TABLE sponsors
    MODIFY updated_at datetime NOT NULL;

ALTER TABLE surveys
    ADD version BIGINT NULL;

ALTER TABLE surveys
    MODIFY updated_at datetime NOT NULL;


ALTER TABLE telemetries
    ADD version BIGINT NULL;

ALTER TABLE telemetries
    MODIFY updated_at datetime NOT NULL;

ALTER TABLE users
    ADD version BIGINT NULL;


UPDATE `boards`
SET version = COALESCE(version, 0)
WHERE version IS NULL;

UPDATE `event_banners`
SET version = COALESCE(version, 0)
WHERE version IS NULL;

UPDATE `events`
SET version = COALESCE(version, 0)
WHERE version IS NULL;

UPDATE `users`
SET version = COALESCE(version, 0)
WHERE version IS NULL;

UPDATE `event_signups`
SET version = COALESCE(version, 0)
WHERE version IS NULL;

UPDATE `event_feedback`
SET version = COALESCE(version, 0)
WHERE version IS NULL;

UPDATE `board_documents`
SET version = COALESCE(version, 0)
WHERE version IS NULL;

UPDATE `contributions`
SET version = COALESCE(version, 0)
WHERE version IS NULL;

UPDATE `event_sign_up_answers`
SET version = COALESCE(version, 0)
WHERE version IS NULL;

UPDATE `memberships`
SET version = COALESCE(version, 0)
WHERE version IS NULL;

UPDATE `files`
SET version = COALESCE(version, 0)
WHERE version IS NULL;

UPDATE `sponsors`
SET version = COALESCE(version, 0)
WHERE version IS NULL;

UPDATE `board_members`
SET version = COALESCE(version, 0)
WHERE version IS NULL;

UPDATE `addresses`
SET version = COALESCE(version, 0)
WHERE version IS NULL;

UPDATE `surveys`
SET version = COALESCE(version, 0)
WHERE version IS NULL;

UPDATE `event_pictures`
SET version = COALESCE(version, 0)
WHERE version IS NULL;

UPDATE `flyway_schema_history`
SET version = COALESCE(version, 0)
WHERE version IS NULL;

UPDATE `committee_members`
SET version = COALESCE(version, 0)
WHERE version IS NULL;

UPDATE `contribution_periods`
SET version = COALESCE(version, 0)
WHERE version IS NULL;

UPDATE `contribution_reminders`
SET version = COALESCE(version, 0)
WHERE version IS NULL;

UPDATE `telemetries`
SET version = COALESCE(version, 0)
WHERE version IS NULL;

UPDATE `guests`
SET version = COALESCE(version, 0)
WHERE version IS NULL;

UPDATE `committees`
SET version = COALESCE(version, 0)
WHERE version IS NULL;

UPDATE `blogs`
SET version = COALESCE(version, 0)
WHERE version IS NULL;

UPDATE `redirects`
SET version = COALESCE(version, 0)
WHERE version IS NULL;

UPDATE `answers`
SET version = COALESCE(version, 0)
WHERE version IS NULL;

UPDATE `questions`
SET version = COALESCE(version, 0)
WHERE version IS NULL;

ALTER TABLE users
    MODIFY updated_at datetime NOT NULL;

ALTER TABLE addresses
    MODIFY version BIGINT NOT NULL;

ALTER TABLE answers
    MODIFY version BIGINT NOT NULL;

ALTER TABLE blogs
    MODIFY version BIGINT NOT NULL;

ALTER TABLE board_documents
    MODIFY version BIGINT NOT NULL;

ALTER TABLE board_members
    MODIFY version BIGINT NOT NULL;

ALTER TABLE boards
    MODIFY version BIGINT NOT NULL;

ALTER TABLE committee_members
    MODIFY version BIGINT NOT NULL;

ALTER TABLE committees
    MODIFY version BIGINT NOT NULL;

ALTER TABLE contribution_periods
    MODIFY version BIGINT NOT NULL;

ALTER TABLE contributions
    MODIFY version BIGINT NOT NULL;

ALTER TABLE event_banners
    MODIFY version BIGINT NOT NULL;

ALTER TABLE event_feedback
    MODIFY version BIGINT NOT NULL;

ALTER TABLE event_pictures
    MODIFY version BIGINT NOT NULL;

ALTER TABLE event_sign_up_answers
    MODIFY version BIGINT NOT NULL;

ALTER TABLE event_signups
    MODIFY version BIGINT NOT NULL;

ALTER TABLE events
    MODIFY version BIGINT NOT NULL;

ALTER TABLE files
    MODIFY version BIGINT NOT NULL;

ALTER TABLE guests
    MODIFY version BIGINT NOT NULL;

ALTER TABLE memberships
    MODIFY version BIGINT NOT NULL;

ALTER TABLE questions
    MODIFY version BIGINT NOT NULL;

ALTER TABLE redirects
    MODIFY version BIGINT NOT NULL;

ALTER TABLE sponsors
    MODIFY version BIGINT NOT NULL;

ALTER TABLE surveys
    MODIFY version BIGINT NOT NULL;

ALTER TABLE telemetries
    MODIFY version BIGINT NOT NULL;

ALTER TABLE users
    MODIFY version BIGINT NOT NULL;

ALTER TABLE contribution_reminders
    ADD CONSTRAINT uk_contribution_reminders_user_period_deleted_at UNIQUE (user_id, contribution_period_id, deleted_at);

ALTER TABLE memberships
    ADD CONSTRAINT uk_memberships_user_id_deleted_at UNIQUE (user_id, deleted_at);

CREATE INDEX idx_contribution_reminders_deleted_at ON contribution_reminders (deleted_at);

CREATE INDEX idx_contributions_created_at ON contributions (created_at);

ALTER TABLE contribution_reminders
    ADD CONSTRAINT fk_contribution_reminders_contribution_period_id FOREIGN KEY (contribution_period_id) REFERENCES contribution_periods (id);


CREATE INDEX idx_contribution_reminders_contribution_period_id ON contribution_reminders (contribution_period_id, deleted_at);

CREATE INDEX idx_contribution_reminders_user_id ON contribution_reminders (user_id, deleted_at);

ALTER TABLE contribution_reminders
    ADD CONSTRAINT fk_contribution_reminders_user_id FOREIGN KEY (user_id) REFERENCES users (id);

UPDATE event_signups
SET created_at = signed_up_at
WHERE signed_up_at IS NOT NULL;

ALTER TABLE event_signups
    DROP COLUMN signed_up_at;

ALTER TABLE sponsors
    MODIFY logo_id BIGINT NULL;