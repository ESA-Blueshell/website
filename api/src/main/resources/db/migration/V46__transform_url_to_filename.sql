ALTER TABLE files
    ADD COLUMN path LONGTEXT;

UPDATE files
SET path = name;

ALTER TABLE files
    MODIFY path LONGTEXT NOT NULL,
    DROP COLUMN url;
