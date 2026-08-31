-- A sent payment request records what it stated, not only that it happened.
--
-- The fee type is stored so the email's reason is the true one rather than one recovered by
-- matching the amount against the period's fees. The amount is stored beside it because the
-- period's fees are editable: deriving it later would make a change of next year's fee
-- rewrite what last year's email is recorded as having said.
ALTER TABLE contribution_reminders
    ADD COLUMN fee_type VARCHAR(32) NULL,
    ADD COLUMN amount DOUBLE NULL,
    ADD COLUMN payment_due_date DATE NULL,
    ADD COLUMN asked_at datetime NULL;

-- Existing rows were asked when they were written. `asked_at` rather than leaning on
-- `updated_at`, which any later touch of the row would move.
UPDATE contribution_reminders
SET asked_at = created_at
WHERE asked_at IS NULL;

ALTER TABLE contribution_reminders
    MODIFY COLUMN asked_at datetime NOT NULL;
