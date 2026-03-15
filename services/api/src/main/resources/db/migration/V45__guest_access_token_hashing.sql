ALTER TABLE guests
    ADD COLUMN access_token_hash VARCHAR(64) NULL AFTER access_token;

UPDATE guests
SET access_token_hash = SHA2(access_token, 256)
WHERE access_token_hash IS NULL;

ALTER TABLE guests
    MODIFY access_token_hash VARCHAR(64) NOT NULL;

ALTER TABLE guests
    DROP INDEX uk_guests_access_token_deleted_at;

ALTER TABLE guests
    ADD CONSTRAINT uk_guests_access_token_hash_deleted_at UNIQUE (access_token_hash, deleted_at);

ALTER TABLE guests
    DROP COLUMN access_token;
