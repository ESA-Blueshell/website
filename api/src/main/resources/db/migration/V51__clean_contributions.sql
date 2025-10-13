DELETE FROM contributions
WHERE NOT paid;

ALTER TABLE contributions
DROP COLUMN reminded_at,
DROP COLUMN paid;