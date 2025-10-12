START TRANSACTION;

CREATE TABLE IF NOT EXISTS addresses
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    house_number MEDIUMTEXT,
    zip_code     MEDIUMTEXT,
    city         MEDIUMTEXT,
    street       VARCHAR(255),
    country      VARCHAR(255),
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted_at   DATETIME NULL,
    user_id      BIGINT   NULL
);

INSERT INTO addresses (street, house_number, zip_code, city, country, created_at, user_id)
SELECT NULLIF(address, ''),
       NULLIF(house_number, ''),
       NULLIF(postal_code, ''),
       NULLIF(city, ''),
       NULLIF(country, ''),
       COALESCE(created_at, CURRENT_TIMESTAMP),
       id
FROM users
WHERE (address IS NOT NULL AND address != '')
   OR (house_number IS NOT NULL AND house_number != '')
   OR (postal_code IS NOT NULL AND postal_code != '')
   OR (city IS NOT NULL AND city != '')
   OR (country IS NOT NULL AND country != '');

ALTER TABLE users
    ADD COLUMN address_id BIGINT NULL;

UPDATE users
    JOIN addresses
SET users.address_id = addresses.id
WHERE addresses.user_id = users.id
AND addresses.user_id IS NOT NUll;

ALTER TABLE addresses
    DROP COLUMN user_id;

ALTER TABLE users
    ADD CONSTRAINT fk_users_address
        FOREIGN KEY (address_id) REFERENCES addresses (id);

ALTER TABLE users
    DROP COLUMN address,
    DROP COLUMN house_number,
    DROP COLUMN postal_code,
    DROP COLUMN city,
    DROP COLUMN street,
    DROP COLUMN country;

COMMIT;
