-- Step 1: Drop the existing primary key constraint on (user_id, committee_id)
ALTER TABLE committee_members
    DROP PRIMARY KEY;

-- Step 2: Add a index on (user_id, committee_id) when not deleted to ensure their combination remains unique
CREATE INDEX user_committee
    ON committee_members (user_id, committee_id);

-- Step 3: Add the new `id` column as the primary key
ALTER TABLE committee_members
    ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY;
