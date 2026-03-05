ALTER TABLE users DROP COLUMN contact_id;
ALTER TABLE contribution_periods DROP INDEX idx_contribution_periods_list_id;
ALTER TABLE contribution_periods DROP COLUMN list_id;
