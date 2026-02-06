CREATE TABLE job_executions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_type VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    payload LONGTEXT NULL,
    error_message LONGTEXT NULL,
    attempts INT NOT NULL DEFAULT 0,
    queued_at DATETIME NULL,
    started_at DATETIME NULL,
    finished_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by_id BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by_id BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

ALTER TABLE job_executions
    ADD CONSTRAINT fk_job_executions_on_created_by FOREIGN KEY (created_by_id) REFERENCES users (id);

ALTER TABLE job_executions
    ADD CONSTRAINT fk_job_executions_on_updated_by FOREIGN KEY (updated_by_id) REFERENCES users (id);

CREATE INDEX idx_job_executions_status ON job_executions (status);
CREATE INDEX idx_job_executions_job_type ON job_executions (job_type);
CREATE INDEX idx_job_executions_created_at ON job_executions (created_at);
