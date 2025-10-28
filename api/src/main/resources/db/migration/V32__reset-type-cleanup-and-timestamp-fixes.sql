-- Concern(s): minor cleanup & timestamp adjustments

/* =========================
   (2) Data – clean reset_type empty string
   ========================= */
UPDATE users
SET reset_type = NULL
WHERE reset_type = '';

/* =========================
   (2) Data – fix event timestamps (instant shift)
   ========================= */
UPDATE events
SET start_time = start_time - INTERVAL 2 HOUR,
    end_time   = end_time - INTERVAL 2 HOUR
WHERE start_time IS NOT NULL
  AND end_time IS NOT NULL;
