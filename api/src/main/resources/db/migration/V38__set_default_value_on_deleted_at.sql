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
