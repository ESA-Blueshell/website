-- Create addresses table
CREATE TABLE addresses (
                           id BIGINT AUTO_INCREMENT PRIMARY KEY,
                           address MEDIUMTEXT,
                           house_number MEDIUMTEXT,
                           postal_code MEDIUMTEXT,
                           city MEDIUMTEXT,
                           street VARCHAR(255),
                           country VARCHAR(255),
                           created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                           deleted_at DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Migrate existing address data from users table to addresses table
INSERT INTO addresses (address, house_number, postal_code, city, street, country, created_at)
SELECT
    CASE WHEN address IS NOT NULL AND address != '' THEN address ELSE NULL END,
    CASE WHEN house_number IS NOT NULL AND house_number != '' THEN house_number ELSE NULL END,
    CASE WHEN postal_code IS NOT NULL AND postal_code != '' THEN postal_code ELSE NULL END,
    CASE WHEN city IS NOT NULL AND city != '' THEN city ELSE NULL END,
    CASE WHEN street IS NOT NULL AND street != '' THEN street ELSE NULL END,
    CASE WHEN country IS NOT NULL AND country != '' THEN country ELSE NULL END,
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
ALTER TABLE users ADD CONSTRAINT fk_users_address FOREIGN KEY (address_id) REFERENCES addresses(id);

-- Update users table to link with addresses
UPDATE users u
    INNER JOIN addresses a ON (
    (u.address IS NULL OR u.address = '' OR a.street = u.address OR (a.address IS NULL AND (u.address IS NULL OR u.address = ''))) AND
    (u.house_number IS NULL OR u.house_number = '' OR a.house_number = u.house_number OR (a.house_number IS NULL AND (u.house_number IS NULL OR u.house_number = ''))) AND
    (u.postal_code IS NULL OR u.postal_code = '' OR a.postal_code = u.postal_code OR (a.postal_code IS NULL AND (u.postal_code IS NULL OR u.postal_code = ''))) AND
    (u.city IS NULL OR u.city = '' OR a.city = u.city OR (a.city IS NULL AND (u.city IS NULL OR u.city = ''))) AND
    (u.country IS NULL OR u.country = '' OR a.country = u.country OR (a.country IS NULL AND (u.country IS NULL OR u.country = ''))) AND
    u.created_at = a.created_at
    )
    SET u.address_id = a.id;

-- Drop old address columns from users table
ALTER TABLE users DROP COLUMN address;
ALTER TABLE users DROP COLUMN house_number;
ALTER TABLE users DROP COLUMN postal_code;
ALTER TABLE users DROP COLUMN city;
ALTER TABLE users DROP COLUMN street;
ALTER TABLE users DROP COLUMN country;