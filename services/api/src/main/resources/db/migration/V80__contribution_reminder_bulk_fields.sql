-- Add amount and payment_due_date fields to contribution_reminders for bulk reminder functionality
ALTER TABLE contribution_reminders ADD COLUMN amount DOUBLE PRECISION NULL;
ALTER TABLE contribution_reminders ADD COLUMN payment_due_date DATE NULL;
