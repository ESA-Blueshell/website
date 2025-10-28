-- Concern(s): small structural changes, signup JSON, committee_members PK, charset, blogs, redirects/telemetries, drop roles, addresses migration
-- Phases: statements kept in the original safe order

/* =========================
   (1) Structures – contributions.reminded_at
   ========================= */
ALTER TABLE contributions
    ADD COLUMN reminded_at datetime;

/* =========================
   (1/2) Structures + data – event_signups.form_answers JSON
   ========================= */
ALTER TABLE event_signups
    ADD COLUMN form_answers_temp JSON;

UPDATE event_signups
SET form_answers_temp =
        CASE
            WHEN JSON_VALID(form_answers) THEN form_answers
            ELSE NULL
            END;

ALTER TABLE event_signups
    DROP COLUMN form_answers;

ALTER TABLE event_signups
    CHANGE COLUMN form_answers_temp form_answers JSON;

/* =========================
   (2) Data – users.reset_type rename
   ========================= */
UPDATE users
SET reset_type = 'USER_ACTIVATION'
WHERE reset_type = 'INITIAL_ACCOUNT_CREATION';

/* =========================
   (1) Structures – committee_members add id PK
   ========================= */
-- Step 1: Drop the existing primary key constraint on (user_id, committee_id)
ALTER TABLE committee_members
    DROP PRIMARY KEY;

-- Step 2: Add a index on (user_id, committee_id) when not deleted to ensure their combination remains unique
CREATE INDEX user_committee
    ON committee_members (user_id, committee_id);

-- Step 3: Add the new `id` column as the primary key
ALTER TABLE committee_members
    ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY;

/* =========================
   (1) Structures – charset/collation to utf8mb4
   ========================= */
ALTER DATABASE blueshell
    CHARACTER SET = utf8mb4
    COLLATE = utf8mb4_unicode_ci;

ALTER TABLE boards
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

ALTER TABLE board_documents
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

ALTER TABLE board_members
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

ALTER TABLE committees
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

ALTER TABLE committee_members
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

ALTER TABLE contributions
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

ALTER TABLE contribution_periods
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

ALTER TABLE events
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

ALTER TABLE event_feedback
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

ALTER TABLE event_pictures
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

ALTER TABLE event_signups
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

ALTER TABLE files
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

ALTER TABLE guests
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

ALTER TABLE memberships
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

ALTER TABLE news
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

ALTER TABLE sponsors
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

ALTER TABLE users
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

/* =========================
   (1) Structures – blogs
   ========================= */
CREATE TABLE blogs
(
    id           bigint       not null auto_increment,
    title        VARCHAR(255) NULL,
    text         MEDIUMTEXT   NULL,
    html         MEDIUMTEXT   NULL,
    markdown     MEDIUMTEXT   NULL,
    published_at datetime     NULL,
    created_at   datetime     NULL,
    deleted_at   datetime     NULL,
    CONSTRAINT PRIMARY KEY (id)
);

/* =========================
   (1) Structures – redirects & telemetries
   ========================= */
CREATE TABLE redirects
(
    id           bigint   not null auto_increment,
    telemetry_id bigint   NULL,
    created_at   datetime NULL,
    deleted_at   datetime NULL,
    CONSTRAINT PRIMARY KEY (id)
);

CREATE TABLE telemetries
(
    id         bigint       not null auto_increment,
    url        VARCHAR(255) NULL,
    platform   SMALLINT     NULL,
    created_at datetime     NULL,
    deleted_at datetime     NULL,
    CONSTRAINT PRIMARY KEY (id)
);

ALTER TABLE redirects
    ADD CONSTRAINT FK_REDIRECTS_ON_TELEMETRY FOREIGN KEY (telemetry_id) REFERENCES telemetries (id);

/* =========================
   (5) Cleanup – drop roles
   ========================= */
DROP TABLE roles;

/* =========================
   (1/2/4/5) Addresses migration (transaction preserved)
   ========================= */
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
