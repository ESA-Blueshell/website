-- Convert TIMESTAMP/DATETIME to DATE by truncating time portion
UPDATE users
SET date_of_birth = DATE(date_of_birth)
WHERE date_of_birth IS NOT NULL;

ALTER TABLE users
    MODIFY COLUMN date_of_birth DATE NULL;

-- CONTRIBUTION_PERIODS.start_date, end_date
UPDATE contribution_periods
SET start_date = DATE(start_date)
WHERE start_date IS NOT NULL;

UPDATE contribution_periods
SET end_date = DATE(end_date)
WHERE end_date IS NOT NULL;

ALTER TABLE contribution_periods
    MODIFY COLUMN start_date DATE NULL,
    MODIFY COLUMN end_date DATE NULL;

-- MEMBERSHIPS.start_date, end_date
UPDATE memberships
SET start_date = DATE(start_date)
WHERE start_date IS NOT NULL;

UPDATE memberships
SET end_date = DATE(end_date)
WHERE end_date IS NOT NULL;

ALTER TABLE memberships
    MODIFY COLUMN start_date DATE NULL,
    MODIFY COLUMN end_date DATE NULL;