ALTER TABLE job_executions
    ADD COLUMN initiated_by_role VARCHAR(32) NOT NULL DEFAULT 'ADMIN' AFTER initiated_by_type;

CREATE INDEX idx_job_executions_initiated_by_role ON job_executions (initiated_by_role);
