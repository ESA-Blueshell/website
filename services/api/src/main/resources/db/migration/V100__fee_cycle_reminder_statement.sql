-- A sent payment request records what it stated, not only that it happened.
--
-- The fee type is stored rather than recovered from the amount: with the type known, the
-- email's stated reason is the true one. The amount itself is not stored — it follows from
-- the type and the period, so a stored copy could only ever disagree with them.
ALTER TABLE contribution_reminders
    ADD COLUMN fee_type VARCHAR(32) NULL,
    ADD COLUMN payment_due_date DATE NULL;
