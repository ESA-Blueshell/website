-- Set session collation to ensure consistency
SET collation_connection = 'utf8mb4_unicode_ci';
SET collation_database = 'utf8mb4_unicode_ci';
SET character_set_client = 'utf8mb4';
SET character_set_connection = 'utf8mb4';
SET character_set_database = 'utf8mb4';
SET character_set_results = 'utf8mb4';

-- Verify database collation
ALTER DATABASE blueshell CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Create addresses table (will inherit database defaults)
CREATE TABLE addresses (
                           id BIGINT AUTO_INCREMENT PRIMARY KEY,
                           house_number MEDIUMTEXT,
                           zip_code MEDIUMTEXT,
                           city MEDIUMTEXT,
                           street VARCHAR(255),
                           country VARCHAR(255),
                           created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                           deleted_at DATETIME NULL
);

-- Migrate existing address data from users table
INSERT INTO addresses (street, house_number, zip_code, city, country, created_at)
SELECT
    address,
    house_number,
    postal_code,
    city,
    country,
    created_at
FROM users
WHERE (address IS NOT NULL AND address != '')
   OR (house_number IS NOT NULL AND house_number != '')
   OR (postal_code IS NOT NULL AND postal_code != '')
   OR (city IS NOT NULL AND city != '')
   OR (street IS NOT NULL AND street != '')
   OR (country IS NOT NULL AND country != '');

-- Add address_id column to users table
ALTER TABLE users ADD COLUMN address_id BIGINT NULL;

-- Create foreign key relationship
ALTER TABLE users ADD CONSTRAINT fk_users_address FOREIGN KEY (address_id) REFERENCES addresses(id);

-- Update users table to link to their addresses
-- Use a more reliable matching strategy with row numbers to handle exact matches
UPDATE users u
    INNER JOIN (
        SELECT
            ROW_NUMBER() OVER (ORDER BY created_at, id) as rn,
            id as addr_id,
            street, house_number, zip_code, city, country, created_at
        FROM addresses
    ) a ON a.rn = (
        SELECT ROW_NUMBER() OVER (ORDER BY u2.created_at, u2.id)
        FROM users u2
        WHERE (u2.address IS NOT NULL AND u2.address != '')
           OR (u2.house_number IS NOT NULL AND u2.house_number != '')
           OR (u2.postal_code IS NOT NULL AND u2.postal_code != '')
           OR (u2.city IS NOT NULL AND u2.city != '')
           OR (u2.country IS NOT NULL AND u2.country != '')
            AND u2.id <= u.id
    )
        AND COALESCE(u.address, '') = COALESCE(a.street, '')
        AND COALESCE(u.house_number, '') = COALESCE(a.house_number, '')
        AND COALESCE(u.postal_code, '') = COALESCE(a.zip_code, '')
        AND COALESCE(u.city, '') = COALESCE(a.city, '')
        AND COALESCE(u.country, '') = COALESCE(a.country, '')
SET u.address_id = a.addr_id;

-- Drop the old address columns from users table
ALTER TABLE users DROP COLUMN address;
ALTER TABLE users DROP COLUMN house_number;
ALTER TABLE users DROP COLUMN postal_code;
ALTER TABLE users DROP COLUMN city;
ALTER TABLE users DROP COLUMN street;
ALTER TABLE users DROP COLUMN country;