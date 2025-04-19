-- Step 1: Drop the existing primary key constraint on (user_id, committee_id)
ALTER TABLE committee_members
    DROP PRIMARY KEY;

-- Step 2: Add a unique constraint on (user_id, committee_id) to ensure their combination remains unique
ALTER TABLE committee_members
    ADD CONSTRAINT unique_user_committee UNIQUE (user_id, committee_id);

-- Step 3: Add the new `id` column as the primary key
ALTER TABLE committee_members
    ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY;

