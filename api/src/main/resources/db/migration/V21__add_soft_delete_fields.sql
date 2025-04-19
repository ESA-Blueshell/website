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