-- Ensure consistent character set / collation (session + DB)
SET collation_connection = 'utf8mb4_unicode_ci';
SET collation_database    = 'utf8mb4_unicode_ci';
SET character_set_client  = 'utf8mb4';
SET character_set_connection = 'utf8mb4';
SET character_set_database   = 'utf8mb4';
SET character_set_results    = 'utf8mb4';

ALTER DATABASE blueshell CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

START TRANSACTION;

-- 1) Create addresses table (final table), with a TEMPORARY user_id
CREATE TABLE IF NOT EXISTS addresses (
                                         id           BIGINT AUTO_INCREMENT PRIMARY KEY,
                                         house_number MEDIUMTEXT,
                                         zip_code     MEDIUMTEXT,
                                         city         MEDIUMTEXT,
                                         street       VARCHAR(255),
                                         country      VARCHAR(255),
                                         created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
                                         deleted_at   DATETIME NULL,
    -- TEMP: used only during migration
                                         user_id      BIGINT NULL,

    -- Optional: generated normalized columns to help dedupe (not persisted after migration)
                                         norm_street  VARCHAR(255)   AS (NULLIF(LOWER(TRIM(street)), '')) PERSISTENT,
                                         norm_house   VARCHAR(255)   AS (NULLIF(LOWER(TRIM(house_number)), '')) PERSISTENT,
                                         norm_zip     VARCHAR(255)   AS (NULLIF(LOWER(TRIM(zip_code)), '')) PERSISTENT,
                                         norm_city    VARCHAR(255)   AS (NULLIF(LOWER(TRIM(city)), '')) PERSISTENT,
                                         norm_country VARCHAR(255)   AS (NULLIF(LOWER(TRIM(country)), '')) PERSISTENT
);

-- 2) Seed addresses from users, keeping per-user rows (duplicates allowed here)
INSERT INTO addresses (street, house_number, zip_code, city, country, created_at, user_id)
SELECT
    NULLIF(address,      ''),
    NULLIF(house_number, ''),
    NULLIF(postal_code,  ''),
    NULLIF(city,         ''),
    NULLIF(country,      ''),
    COALESCE(created_at, CURRENT_TIMESTAMP),
    id  -- keep the user_id to map back later
FROM users
WHERE (address IS NOT NULL AND address != '')
   OR (house_number IS NOT NULL AND house_number != '')
   OR (postal_code IS NOT NULL AND postal_code != '')
   OR (city IS NOT NULL AND city != '')
   OR (country IS NOT NULL AND country != '');

-- 3) Prepare users table for the FK (NULL for users without an address)
ALTER TABLE users ADD COLUMN address_id BIGINT NULL;

-- 4) Build a canonical (deduplicated) mapping of address keys -> canonical address id
-- Choose the earliest created row for each unique normalized combo
CREATE TEMPORARY TABLE address_canonical AS
SELECT
    MIN(id) AS canonical_id,
    norm_street, norm_house, norm_zip, norm_city, norm_country
FROM addresses
GROUP BY norm_street, norm_house, norm_zip, norm_city, norm_country;

-- 5) Map each user to the canonical address using the row we inserted for them (via user_id)
UPDATE users u
    JOIN addresses a
    ON a.user_id = u.id
    JOIN address_canonical c
    ON c.norm_street  <=> a.norm_street
        AND c.norm_house   <=> a.norm_house
        AND c.norm_zip     <=> a.norm_zip
        AND c.norm_city    <=> a.norm_city
        AND c.norm_country <=> a.norm_country
SET u.address_id = c.canonical_id;

-- 6) Remove duplicate address rows, keeping only canonical ones
DELETE a2 FROM addresses a2
                   LEFT JOIN address_canonical c
                             ON c.canonical_id = a2.id
WHERE c.canonical_id IS NULL;

-- 7) Cleanup: drop temp/helper columns and indexes no longer needed
ALTER TABLE addresses
    DROP COLUMN user_id,
    DROP COLUMN norm_street,
    DROP COLUMN norm_house,
    DROP COLUMN norm_zip,
    DROP COLUMN norm_city,
    DROP COLUMN norm_country;

-- 8) Only now, enforce the FK (data is clean and deduped)
ALTER TABLE users
    ADD CONSTRAINT fk_users_address
        FOREIGN KEY (address_id) REFERENCES addresses(id);

-- 9) Drop old address columns from users
ALTER TABLE users
    DROP COLUMN address,
    DROP COLUMN house_number,
    DROP COLUMN postal_code,
    DROP COLUMN city,
    DROP COLUMN street,
    DROP COLUMN country;

COMMIT;

-- Optional but recommended: useful indexes for lookup/joins (post-migration)
-- ALTER TABLE addresses ADD INDEX idx_addresses_city_zip (city(100), zip_code(100));
