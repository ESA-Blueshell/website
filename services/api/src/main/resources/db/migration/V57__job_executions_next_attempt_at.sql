ALTER TABLE job_executions
    ADD COLUMN next_attempt_at DATETIME NULL AFTER finished_at;

CREATE INDEX idx_job_executions_due_retry ON job_executions (status, next_attempt_at);
