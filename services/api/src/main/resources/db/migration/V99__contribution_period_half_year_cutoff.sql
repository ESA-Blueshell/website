-- The half-year cutoff becomes policy for the year rather than an input retyped on every send.
--
-- A regular membership starting after this date pays the half-year fee; one starting on it or
-- before pays the full year. Living on the period is what stops a send made in February and one
-- in June from silently applying different cutoffs.
ALTER TABLE contribution_periods
    ADD COLUMN half_year_cutoff_date DATE NULL AFTER end_date;

-- Existing periods get the midpoint rounded up to the first of the following month, clamped
-- inside the period. A null would leave a period unable to resolve a fee at all, and the
-- treasurer can move the date once they see it.
UPDATE contribution_periods
SET half_year_cutoff_date = LEAST(
        end_date,
        GREATEST(
                start_date,
                DATE_FORMAT(
                        DATE_ADD(
                                DATE_ADD(start_date, INTERVAL FLOOR(DATEDIFF(end_date, start_date) / 2) DAY),
                                INTERVAL 1 MONTH
                        ),
                        '%Y-%m-01'
                )
        )
    )
WHERE half_year_cutoff_date IS NULL;

ALTER TABLE contribution_periods
    MODIFY COLUMN half_year_cutoff_date DATE NOT NULL;
