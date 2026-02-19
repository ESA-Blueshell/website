ALTER TABLE job_executions
    ADD COLUMN error_type VARCHAR(255) NULL AFTER error_message,
    ADD COLUMN error_reason LONGTEXT NULL AFTER error_type;
