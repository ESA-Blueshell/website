UPDATE events
SET start_time = start_time - INTERVAL 2 HOUR,
    end_time   = end_time - INTERVAL 2 HOUR
WHERE start_time IS NOT NULL
  AND end_time IS NOT NULL;