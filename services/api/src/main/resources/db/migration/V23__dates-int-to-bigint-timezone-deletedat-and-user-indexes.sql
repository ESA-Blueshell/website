-- Concern(s): date normalization, list_id nullability, events end_time TZ conversion, cleanup, soft-delete defaults, users UQs, int->bigint
-- Order-critical session/transaction contexts preserved (V36)

/* =========================
   (2/1) Data then structures – convert timestamp/datetime to DATE
   ========================= */
-- Convert TIMESTAMP/DATETIME to DATE by truncating time portion
UPDATE users
SET date_of_birth = DATE(date_of_birth)
WHERE date_of_birth IS NOT NULL;

ALTER TABLE users
    MODIFY COLUMN date_of_birth DATE NULL;

-- CONTRIBUTION_PERIODS.start_date, end_date
UPDATE contribution_periods
SET start_date = DATE(start_date)
WHERE start_date IS NOT NULL;

UPDATE contribution_periods
SET end_date = DATE(end_date)
WHERE end_date IS NOT NULL;

ALTER TABLE contribution_periods
    MODIFY COLUMN start_date DATE NULL,
    MODIFY COLUMN end_date DATE NULL;

-- MEMBERSHIPS.start_date, end_date
UPDATE memberships
SET start_date = DATE(start_date)
WHERE start_date IS NOT NULL;

UPDATE memberships
SET end_date = DATE(end_date)
WHERE end_date IS NOT NULL;

ALTER TABLE memberships
    MODIFY COLUMN start_date DATE NULL,
    MODIFY COLUMN end_date DATE NULL;

/* =========================
   (1) Structures – allow NULL for contribution_periods.list_id
   ========================= */
ALTER TABLE contribution_periods
    MODIFY COLUMN list_id INT NULL;

/* =========================
   (1/2/5) Events.end_time TIMESTAMP -> DATETIME (UTC); preserve session TZ and transaction
   ========================= */
-- Converts events.end_time from TIMESTAMP to DATETIME (kept in UTC).

-- 1) Work in UTC for this session only, so we don't depend on the DB/server TZ.
SET @old_tz := @@session.time_zone;
SET time_zone = '+00:00';

START TRANSACTION;

-- 2) Add a temporary DATETIME column.
--    Mirror nullability of your existing column (adjust NOT NULL if needed).
ALTER TABLE events
    ADD COLUMN end_time_dt DATETIME NULL;

-- 3) Copy values from TIMESTAMP -> DATETIME while the session TZ is UTC.
--    Because session TZ is UTC, this preserves the exact instant as a UTC wall time.
UPDATE events
SET end_time_dt = CAST(end_time AS DATETIME);

-- 4) Drop the old TIMESTAMP column and rename the new one.
ALTER TABLE events
    DROP COLUMN end_time,
    CHANGE COLUMN end_time_dt end_time DATETIME NULL;

COMMIT;

-- 5) Restore previous session TZ.
SET time_zone = @old_tz;

/* =========================
   (2) Data – remove committee_members whose users were deleted
   ========================= */
DELETE cm
FROM committee_members AS cm
         LEFT JOIN users AS u ON u.id = cm.user_id
WHERE u.id IS NULL;

/* =========================
   (2/3/1) Data to set deleted_at sentinel then structure to not null default
   ========================= */
-- [Large block setting deleted_at defaults across tables]
-- (Kept byte-for-byte to preserve semantics with later triggers and unique constraints)

-- =========================
-- addresses
-- =========================
UPDATE addresses
SET deleted_at = '9999-12-31 23:59:59'
WHERE deleted_at IS NULL;

ALTER TABLE addresses
    MODIFY COLUMN deleted_at DATETIME NOT NULL DEFAULT '9999-12-31 23:59:59';

-- =========================
-- blogs
-- =========================
UPDATE blogs
SET deleted_at = '9999-12-31 23:59:59'
WHERE deleted_at IS NULL;

ALTER TABLE blogs
    MODIFY COLUMN deleted_at DATETIME NOT NULL DEFAULT '9999-12-31 23:59:59';

-- =========================
-- boards
-- =========================
UPDATE boards
SET deleted_at = '9999-12-31 23:59:59'
WHERE deleted_at IS NULL;

ALTER TABLE boards
    MODIFY COLUMN deleted_at DATETIME NOT NULL DEFAULT '9999-12-31 23:59:59';

-- =========================
-- board_documents
-- =========================
UPDATE board_documents
SET deleted_at = '9999-12-31 23:59:59'
WHERE deleted_at IS NULL;

ALTER TABLE board_documents
    MODIFY COLUMN deleted_at DATETIME NOT NULL DEFAULT '9999-12-31 23:59:59';

-- =========================
-- board_members
-- =========================
UPDATE board_members
SET deleted_at = '9999-12-31 23:59:59'
WHERE deleted_at IS NULL;

ALTER TABLE board_members
    MODIFY COLUMN deleted_at DATETIME NOT NULL DEFAULT '9999-12-31 23:59:59';

-- =========================
-- committees
-- =========================
UPDATE committees
SET deleted_at = '9999-12-31 23:59:59'
WHERE deleted_at IS NULL;

ALTER TABLE committees
    MODIFY COLUMN deleted_at DATETIME NOT NULL DEFAULT '9999-12-31 23:59:59';

-- =========================
-- committee_members
-- =========================
UPDATE committee_members
SET deleted_at = '9999-12-31 23:59:59'
WHERE deleted_at IS NULL;

ALTER TABLE committee_members
    MODIFY COLUMN deleted_at DATETIME NOT NULL DEFAULT '9999-12-31 23:59:59';

-- =========================
-- contributions
-- =========================
UPDATE contributions
SET deleted_at = '9999-12-31 23:59:59'
WHERE deleted_at IS NULL;

ALTER TABLE contributions
    MODIFY COLUMN deleted_at DATETIME NOT NULL DEFAULT '9999-12-31 23:59:59';

-- =========================
-- contribution_periods
-- =========================
UPDATE contribution_periods
SET deleted_at = '9999-12-31 23:59:59'
WHERE deleted_at IS NULL;

ALTER TABLE contribution_periods
    MODIFY COLUMN deleted_at DATETIME NOT NULL DEFAULT '9999-12-31 23:59:59';

-- =========================
-- events
-- =========================
UPDATE events
SET deleted_at = '9999-12-31 23:59:59'
WHERE deleted_at IS NULL;

ALTER TABLE events
    MODIFY COLUMN deleted_at DATETIME NOT NULL DEFAULT '9999-12-31 23:59:59';

-- =========================
-- event_feedback
-- =========================
UPDATE event_feedback
SET deleted_at = '9999-12-31 23:59:59'
WHERE deleted_at IS NULL;

ALTER TABLE event_feedback
    MODIFY COLUMN deleted_at DATETIME NOT NULL DEFAULT '9999-12-31 23:59:59';

-- =========================
-- event_pictures
-- =========================
UPDATE event_pictures
SET deleted_at = '9999-12-31 23:59:59'
WHERE deleted_at IS NULL;

ALTER TABLE event_pictures
    MODIFY COLUMN deleted_at DATETIME NOT NULL DEFAULT '9999-12-31 23:59:59';

-- =========================
-- event_signups
-- =========================
UPDATE event_signups
SET deleted_at = '9999-12-31 23:59:59'
WHERE deleted_at IS NULL;

ALTER TABLE event_signups
    MODIFY COLUMN deleted_at DATETIME NOT NULL DEFAULT '9999-12-31 23:59:59';

-- =========================
-- files
-- =========================
UPDATE files
SET deleted_at = '9999-12-31 23:59:59'
WHERE deleted_at IS NULL;

ALTER TABLE files
    MODIFY COLUMN deleted_at DATETIME NOT NULL DEFAULT '9999-12-31 23:59:59';

-- =========================
-- guests
-- =========================
UPDATE guests
SET deleted_at = '9999-12-31 23:59:59'
WHERE deleted_at IS NULL;

ALTER TABLE guests
    MODIFY COLUMN deleted_at DATETIME NOT NULL DEFAULT '9999-12-31 23:59:59';

-- =========================
-- memberships
-- =========================
UPDATE memberships
SET deleted_at = '9999-12-31 23:59:59'
WHERE deleted_at IS NULL;

ALTER TABLE memberships
    MODIFY COLUMN deleted_at DATETIME NOT NULL DEFAULT '9999-12-31 23:59:59';

-- =========================
-- redirects
-- =========================
UPDATE redirects
SET deleted_at = '9999-12-31 23:59:59'
WHERE deleted_at IS NULL;

ALTER TABLE redirects
    MODIFY COLUMN deleted_at DATETIME NOT NULL DEFAULT '9999-12-31 23:59:59';

-- =========================
-- sponsors
-- =========================
UPDATE sponsors
SET deleted_at = '9999-12-31 23:59:59'
WHERE deleted_at IS NULL;

ALTER TABLE sponsors
    MODIFY COLUMN deleted_at DATETIME NOT NULL DEFAULT '9999-12-31 23:59:59';

-- =========================
-- telemetries
-- =========================
UPDATE telemetries
SET deleted_at = '9999-12-31 23:59:59'
WHERE deleted_at IS NULL;

ALTER TABLE telemetries
    MODIFY COLUMN deleted_at DATETIME NOT NULL DEFAULT '9999-12-31 23:59:59';

-- =========================
-- users
-- =========================
UPDATE users
SET deleted_at = '9999-12-31 23:59:59'
WHERE deleted_at IS NULL;

UPDATE users
SET discord = NULL
WHERE discord = '';

ALTER TABLE users
    MODIFY COLUMN deleted_at DATETIME NOT NULL DEFAULT '9999-12-31 23:59:59';

/* =========================
   (2/4) Data cleanup then UQ indexes on users
   ========================= */
DELETE m
FROM memberships m
         JOIN users u ON u.id = m.user_id
WHERE u.email IN ('us@er', 'ad@min', 'lou@uis');

DELETE u
FROM users u
WHERE u.email IN ('us@er', 'ad@min', 'lou@uis');

CREATE UNIQUE INDEX uk_users_username ON users (username, deleted_at);
CREATE UNIQUE INDEX uk_users_email ON users (email, deleted_at);
CREATE UNIQUE INDEX uk_users_phone_number ON users (phone_number, deleted_at);
CREATE UNIQUE INDEX uk_users_discord ON users (discord, deleted_at);

/* =========================
   (1/3) Structures – memberships.country
   ========================= */
ALTER TABLE memberships
    ADD COLUMN country VARCHAR(2) NOT NULL DEFAULT 'NL';

/* =========================
   (1) Structures – widen to BIGINT where needed
   ========================= */
ALTER TABLE event_signups
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE contribution_periods
    MODIFY COLUMN list_id BIGINT NULL;
