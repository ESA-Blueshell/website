-- Drop columns that are no longer needed and add new columns for membership info and payment method
ALTER TABLE users
    DROP COLUMN contribution_paid,
    DROP COLUMN online_signup,
    ADD COLUMN incasso     BOOLEAN DEFAULT FALSE;

-- Remove the old Brevo contact field and add a new contact_id field
ALTER TABLE users
    DROP COLUMN in_brevo,
    ADD COLUMN contact_id BIGINT;

-- Remove columns related to study data which are no longer required
ALTER TABLE users
    DROP COLUMN study,
    DROP COLUMN start_study_year;

-- Update reset_key values to reflect the new account activation status
UPDATE users
    SET reset_key = 'ACCOUNT_ACTIVATION'
    WHERE reset_key = 'INITIAL_ACCOUNT_CREATION';