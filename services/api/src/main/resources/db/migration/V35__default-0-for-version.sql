-- Concern(s): default version = 0 everywhere

ALTER TABLE addresses
    ALTER version SET DEFAULT 0;

ALTER TABLE answers
    ALTER version SET DEFAULT 0;

ALTER TABLE blogs
    ALTER version SET DEFAULT 0;

ALTER TABLE board_documents
    ALTER version SET DEFAULT 0;

ALTER TABLE board_members
    ALTER version SET DEFAULT 0;

ALTER TABLE boards
    ALTER version SET DEFAULT 0;

ALTER TABLE committee_members
    ALTER version SET DEFAULT 0;

ALTER TABLE committees
    ALTER version SET DEFAULT 0;

ALTER TABLE contribution_periods
    ALTER version SET DEFAULT 0;

ALTER TABLE contribution_reminders
    ALTER version SET DEFAULT 0;

ALTER TABLE contributions
    ALTER version SET DEFAULT 0;

ALTER TABLE event_banners
    ALTER version SET DEFAULT 0;

ALTER TABLE event_feedback
    ALTER version SET DEFAULT 0;

ALTER TABLE event_pictures
    ALTER version SET DEFAULT 0;

ALTER TABLE event_sign_up_answers
    ALTER version SET DEFAULT 0;

ALTER TABLE event_signups
    ALTER version SET DEFAULT 0;

ALTER TABLE events
    ALTER version SET DEFAULT 0;

ALTER TABLE files
    ALTER version SET DEFAULT 0;

ALTER TABLE guests
    ALTER version SET DEFAULT 0;

ALTER TABLE memberships
    ALTER version SET DEFAULT 0;

ALTER TABLE questions
    ALTER version SET DEFAULT 0;

ALTER TABLE recovery_tokens
    ALTER version SET DEFAULT 0;

ALTER TABLE redirects
    ALTER version SET DEFAULT 0;

ALTER TABLE sponsors
    ALTER version SET DEFAULT 0;

ALTER TABLE surveys
    ALTER version SET DEFAULT 0;

ALTER TABLE telemetries
    ALTER version SET DEFAULT 0;

ALTER TABLE users
    ALTER version SET DEFAULT 0;
