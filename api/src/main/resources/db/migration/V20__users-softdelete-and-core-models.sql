-- Concern(s): users, soft deletes, legacy table cleanup, core models (files, boards, memberships, links)
-- Phases:
--   (1) Create & evolve structures
--   (2) Data moves & inserts
--   (3) Defaults & computed metadata
--   (4) Constraints & indexes
--   (5) Cleanup

/* =========================
   (1) Structures – users field changes
   ========================= */
-- Drop columns that are no longer needed and add new columns for membership info and payment method
ALTER TABLE users
    DROP COLUMN contribution_paid,
    DROP COLUMN online_signup,
    ADD COLUMN incasso BOOLEAN DEFAULT FALSE;

-- Remove the old Brevo contact field and add a new contact_id field
ALTER TABLE users
    DROP COLUMN in_brevo,
    ADD COLUMN contact_id BIGINT;

-- Update reset_key values to reflect the new account activation status
UPDATE users
SET reset_key = 'ACCOUNT_ACTIVATION'
WHERE reset_key = 'INITIAL_ACCOUNT_CREATION';

/* =========================
   (1) Structures – add soft-delete fields
   ========================= */
ALTER TABLE committee_members
    ADD COLUMN deleted_at DATETIME;

ALTER TABLE contribution_periods
    ADD COLUMN deleted_at DATETIME;

ALTER TABLE contributions
    ADD COLUMN deleted_at DATETIME;

ALTER TABLE event_feedback
    ADD COLUMN deleted_at DATETIME;

ALTER TABLE event_signups
    ADD COLUMN deleted_at DATETIME;

ALTER TABLE events
    ADD COLUMN deleted_at DATETIME;

ALTER TABLE guests
    ADD COLUMN deleted_at DATETIME;

ALTER TABLE news
    ADD COLUMN deleted_at DATETIME;

ALTER TABLE pictures
    ADD COLUMN deleted_at DATETIME;

ALTER TABLE signatures
    ADD COLUMN deleted_at DATETIME;

ALTER TABLE sponsors
    ADD COLUMN deleted_at DATETIME;

/* =========================
   (5) Cleanup – drop unused legacy tables
   ========================= */
-- drop all tables which are not used in the application
DROP TABLE billables;
DROP TABLE registrations;

/* =========================
   (1) Structures – new core models
   ========================= */
CREATE TABLE board_documents
(
    id         BIGINT AUTO_INCREMENT NOT NULL,
    board_id   BIGINT                NULL,
    name       VARCHAR(255)          NULL,
    file_id    BIGINT                NULL,
    deleted_at datetime              NULL,
    CONSTRAINT PRIMARY KEY (id)
);

CREATE TABLE board_members
(
    id         BIGINT AUTO_INCREMENT NOT NULL,
    board_id   BIGINT                NULL,
    user_id    BIGINT                NULL,
    picture_id BIGINT                NULL,
    deleted_at datetime              NULL,
    CONSTRAINT PRIMARY KEY (id)
);

CREATE TABLE boards
(
    id         BIGINT AUTO_INCREMENT NOT NULL,
    name       VARCHAR(255)          NULL,
    picture_id BIGINT                NULL,
    candidate  VARCHAR(255)          NULL,
    start_date date                  NULL,
    end_date   date                  NULL,
    deleted_at datetime              NULL,
    CONSTRAINT PRIMARY KEY (id)
);

CREATE TABLE event_pictures
(
    id         BIGINT AUTO_INCREMENT NOT NULL,
    picture_id BIGINT                NULL,
    event_id   BIGINT                NULL,
    deleted_at datetime              NULL,
    CONSTRAINT PRIMARY KEY (id)
);

CREATE TABLE files
(
    id          BIGINT AUTO_INCREMENT NOT NULL,
    name        VARCHAR(255)          NULL,
    url         VARCHAR(255)          NULL,
    uploader_id BIGINT                NULL,
    created_at  datetime              NULL,
    media_type  VARCHAR(255)          NULL,
    size        BIGINT                NULL,
    type        VARCHAR(255)          NULL,
    deleted_at  datetime              NULL,
    CONSTRAINT PRIMARY KEY (id)
);

CREATE TABLE memberships
(
    id           BIGINT AUTO_INCREMENT NOT NULL,
    user_id      BIGINT                NULL,
    start_date   date                  NULL,
    end_date     date                  NULL,
    type         VARCHAR(255)          NULL,
    city         VARCHAR(255)          NULL,
    incasso      tinyint(1)            NULL,
    signature_id BIGINT                NULL,
    deleted_at   datetime              NULL,
    CONSTRAINT PRIMARY KEY (id)
);

/* =========================
   (4) Constraints & indexes – FKs as in originals
   ========================= */
ALTER TABLE boards
    ADD CONSTRAINT FK_BOARDS_ON_PICTURE FOREIGN KEY (picture_id) REFERENCES files (id);

ALTER TABLE board_documents
    ADD CONSTRAINT FK_BOARD_DOCUMENTS_ON_BOARD FOREIGN KEY (board_id) REFERENCES boards (id);

ALTER TABLE board_documents
    ADD CONSTRAINT FK_BOARD_DOCUMENTS_ON_FILE FOREIGN KEY (file_id) REFERENCES files (id);

ALTER TABLE board_members
    ADD CONSTRAINT FK_BOARD_MEMBERS_ON_BOARD FOREIGN KEY (board_id) REFERENCES boards (id);

ALTER TABLE board_members
    ADD CONSTRAINT FK_BOARD_MEMBERS_ON_PICTURE FOREIGN KEY (picture_id) REFERENCES files (id);

ALTER TABLE board_members
    ADD CONSTRAINT FK_BOARD_MEMBERS_ON_USER FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE committee_members
    ADD CONSTRAINT FK_COMMITTEE_MEMBERS_ON_COMMITTEE FOREIGN KEY (committee_id) REFERENCES committees (id);

ALTER TABLE contributions
    ADD COLUMN member_id BIGINT;

ALTER TABLE contributions
    ADD CONSTRAINT FK_CONTRIBUTIONS_ON_MEMBER FOREIGN KEY (member_id) REFERENCES memberships (id);

ALTER TABLE event_pictures
    ADD CONSTRAINT FK_EVENT_PICTURES_ON_EVENT FOREIGN KEY (event_id) REFERENCES events (id);

ALTER TABLE event_pictures
    ADD CONSTRAINT FK_EVENT_PICTURES_ON_PICTURE FOREIGN KEY (picture_id) REFERENCES files (id);

ALTER TABLE event_signups
    ADD CONSTRAINT FK_EVENT_SIGNUPS_ON_EVENT FOREIGN KEY (event_id) REFERENCES events (id);

ALTER TABLE files
    ADD CONSTRAINT FK_FILES_ON_UPLOADER FOREIGN KEY (uploader_id) REFERENCES users (id);

ALTER TABLE memberships
    ADD CONSTRAINT FK_MEMBERSHIPS_ON_SIGNATURE FOREIGN KEY (signature_id) REFERENCES files (id);

ALTER TABLE memberships
    ADD CONSTRAINT FK_MEMBERSHIPS_ON_USER FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE sponsors
    ADD CONSTRAINT FK_SPONSORS_ON_LOGO FOREIGN KEY (logo_id) REFERENCES files (id);

ALTER TABLE users
    ADD COLUMN creator_id BIGINT;

ALTER TABLE users
    ADD CONSTRAINT FK_USERS_ON_CREATOR FOREIGN KEY (creator_id) REFERENCES users (id);

ALTER TABLE users
    ADD CONSTRAINT FK_USERS_ON_PROFILE_PICTURE FOREIGN KEY (profile_picture) REFERENCES files (id);
