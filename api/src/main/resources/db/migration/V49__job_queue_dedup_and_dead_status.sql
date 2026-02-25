ALTER TABLE job_executions ADD COLUMN dedup_key VARCHAR(255) NULL AFTER finished_at;
CREATE INDEX idx_job_executions_dedup ON job_executions (job_type, dedup_key, status);
