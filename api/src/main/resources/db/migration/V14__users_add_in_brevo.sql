ALTER TABLE users
    ADD COLUMN in_brevo BOOLEAN;

UPDATE users
SET in_brevo = FALSE;
