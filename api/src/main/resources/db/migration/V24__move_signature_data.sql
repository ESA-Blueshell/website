-- Create a temporary table to map signature IDs to new file IDs
CREATE TEMPORARY TABLE IF NOT EXISTS signature_file_mapping
(
    signature_id BIGINT PRIMARY KEY,
    file_id      BIGINT,
    city         VARCHAR(255),
    incasso      tinyint(1)
);

-- Insert relevant data from signatures into files, capturing the ID mapping
INSERT INTO files (name, url, uploader_id, created_at, media_type, size, type)
SELECT name,
       url,
       user_id,
       created_at,
       'image/png' as mediatype,
       NULL        as size,
       'SIGNATURE' as type
FROM signatures;

-- Populate the temporary table with the mapping
INSERT INTO signature_file_mapping (signature_id, file_id, city)
SELECT s.id,
       f.id,
       s.city
FROM signatures s
         JOIN files f ON s.name = f.name COLLATE utf8mb4_general_ci;

-- Insert data into members using the captured file IDs
INSERT INTO memberships (user_id,
                     start_date,
                     end_date,
                     type,
                     city,
                     incasso,
                     signature_id)
SELECT s.user_id as user_id,
       s.date    AS start_date,
       NULL      AS end_date,
       'REGULAR' AS type,
       s.city    AS city,
       u.incasso AS incasso,
       m.file_id AS signature_id
FROM signatures s
         JOIN signature_file_mapping m ON s.id = m.signature_id
         JOIN users u ON u.id = s.user_id;


INSERT INTO memberships (user_id, start_date, end_date, type, city, incasso, signature_id)
SELECT u.id                 AS user_id,
       DATE(u.member_since) AS start_date,
       NULL                 AS end_date,
       'REGULAR'            AS type,
       NULL                 AS city,
       u.incasso            AS incasso,
       NULL                 AS signature_id
FROM users u WHERE u.member_since <= CURRENT_TIME AND u.id NOT IN (SELECT user_id FROM memberships);


-- Cleanup temporary table
DROP TEMPORARY TABLE IF EXISTS signature_file_mapping;
-- Drop signature table
DROP TABLE signatures;

-- Create temporary table to map picture IDs to new file IDs
CREATE TEMPORARY TABLE IF NOT EXISTS picture_file_mapping
(
    picture_id BIGINT,
    file_id    BIGINT
);

-- Insert relevant data from pictures into files with proper MIME types
INSERT INTO files (name, url, uploader_id, created_at, media_type, type)
SELECT p.name,
       p.url,
       p.uploader_id,
       p.created_at,
       CASE
           WHEN LOWER(SUBSTRING_INDEX(p.url, '.', -1)) = 'png' THEN 'image/png'
           WHEN LOWER(SUBSTRING_INDEX(p.url, '.', -1)) = 'gif' THEN 'image/gif'
           WHEN LOWER(SUBSTRING_INDEX(p.url, '.', -1)) IN ('jpg', 'jpeg', 'jfif') THEN 'image/jpeg'
           ELSE 'application/octet-stream'
           END        AS media_type,
       'EVENT_BANNER' AS type
FROM pictures p;

-- Create a temporary table to map picture IDs to new file IDs
CREATE TEMPORARY TABLE IF NOT EXISTS picture_file_mapping
(
    picture_id BIGINT,
    file_id    BIGINT
);

-- Capture the maximum file ID before inserting new files to identify new entries later
SET @max_file_id_before = (SELECT COALESCE(MAX(id), 0)
                           FROM files);

-- Temporary table to track the order of pictures with a generated row number
CREATE TEMPORARY TABLE temp_picture_order AS
SELECT p.id                                   AS picture_id,
       p.name,
       p.url,
       p.uploader_id,
       p.created_at,
       @picture_rownum := @picture_rownum + 1 AS rownum
FROM (SELECT @picture_rownum := 0) r,
     pictures p
ORDER BY p.id;

-- Insert into files while preserving the order of pictures
INSERT INTO files (name,
                   url,
                   uploader_id,
                   created_at,
                   media_type,
                   type)
SELECT tpo.name,
       tpo.url,
       tpo.uploader_id,
       tpo.created_at,
       CASE
           WHEN LOWER(SUBSTRING_INDEX(tpo.url, '.', -1)) = 'png' THEN 'image/png'
           WHEN LOWER(SUBSTRING_INDEX(tpo.url, '.', -1)) = 'gif' THEN 'image/gif'
           WHEN LOWER(SUBSTRING_INDEX(tpo.url, '.', -1)) IN ('jpg', 'jpeg', 'jfif') THEN 'image/jpeg'
           ELSE 'application/octet-stream'
           END        AS media_type,
       'EVENT_BANNER' AS type
FROM temp_picture_order tpo
ORDER BY tpo.rownum;

-- Temporary table to track the order of newly inserted files
CREATE TEMPORARY TABLE temp_file_order AS
SELECT f.id                             AS file_id,
       @file_rownum := @file_rownum + 1 AS rownum
FROM (SELECT @file_rownum := 0) r,
     files f
WHERE f.id > @max_file_id_before
  AND f.type = 'EVENT_BANNER'
ORDER BY f.id;

-- Map each picture to its corresponding file based on insertion order
INSERT INTO picture_file_mapping (picture_id, file_id)
SELECT tpo.picture_id,
       tfo.file_id
FROM temp_picture_order tpo
         JOIN temp_file_order tfo ON tpo.rownum = tfo.rownum;

-- Update events with the correct file IDs
UPDATE events e
    JOIN picture_file_mapping m ON e.banner_id = m.picture_id
SET e.banner_id = m.file_id;

-- Add foreign key constraint
ALTER TABLE events
    ADD CONSTRAINT fk_events_banner_file
        FOREIGN KEY (banner_id) REFERENCES files (id);

-- Cleanup temporary tables
DROP TEMPORARY TABLE IF EXISTS temp_picture_order;
DROP TEMPORARY TABLE IF EXISTS temp_file_order;
DROP TEMPORARY TABLE IF EXISTS picture_file_mapping;

-- Drop pictures table
DROP TABLE pictures;

ALTER TABLE users
    DROP COLUMN member_since,
    DROP COLUMN incasso;