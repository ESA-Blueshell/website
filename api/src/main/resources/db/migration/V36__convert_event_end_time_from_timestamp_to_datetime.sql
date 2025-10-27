-- Converts events.end_time from TIMESTAMP to DATETIME (kept in UTC).

-- 1) Work in UTC for this session only, so we don't depend on the DB/server TZ.
SET @old_tz := @@session.time_zone;
SET time_zone = '+00:00';

START TRANSACTION;

-- 2) Add a temporary DATETIME column.
--    Mirror nullability of your existing column (adjust NOT NULL if needed).
ALTER TABLE events
    ADD COLUMN end_time_dt DATETIME NULL;

-- 3) Copy values from TIMESTAMP -> DATETIME while the session TZ is UTC.
--    Because session TZ is UTC, this preserves the exact instant as a UTC wall time.
UPDATE events
SET end_time_dt = CAST(end_time AS DATETIME);

-- 4) Drop the old TIMESTAMP column and rename the new one.
ALTER TABLE events
DROP COLUMN end_time,
  CHANGE COLUMN end_time_dt end_time DATETIME NULL;

COMMIT;

-- 5) Restore previous session TZ.
SET time_zone = @old_tz;
