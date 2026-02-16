ALTER TABLE job_executions
    ADD COLUMN initiated_by_user_id BIGINT NULL AFTER finished_at,
    ADD COLUMN initiated_by_type VARCHAR(16) NOT NULL DEFAULT 'SYSTEM' AFTER initiated_by_user_id;

ALTER TABLE job_executions
    ADD CONSTRAINT fk_job_executions_on_initiated_by_user FOREIGN KEY (initiated_by_user_id) REFERENCES users (id);

CREATE INDEX idx_job_executions_initiated_by_user_id ON job_executions (initiated_by_user_id);
CREATE INDEX idx_job_executions_initiated_by_type ON job_executions (initiated_by_type);
