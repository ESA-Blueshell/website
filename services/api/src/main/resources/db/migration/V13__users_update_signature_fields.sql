ALTER TABLE users
    ADD COLUMN online_signup BOOLEAN;


UPDATE users
SET online_signup = TRUE,
    member_since  = created_at
WHERE signature_id IS NOT NULL;

UPDATE users
SET online_signup = FALSE
WHERE online_signup IS NULL;
