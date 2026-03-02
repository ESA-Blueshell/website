ALTER TABLE job_executions ADD COLUMN dedup_key VARCHAR(255) NULL AFTER finished_at;
CREATE INDEX idx_job_executions_dedup ON job_executions (job_type, dedup_key, status);

-- Migrate old job types that were replaced by unified sync handlers.
-- All legacy rows are marked SUCCESS so they are never retried.

UPDATE job_executions
SET job_type = 'calendar.sync-event',
    status   = 'SUCCESS'
WHERE job_type IN ('calendar.add-event', 'calendar.remove-event', 'calendar.sync-event');

UPDATE job_executions
SET job_type = 'contact.sync-list-membership',
    status   = 'SUCCESS'
WHERE job_type IN ('contact.add-to-list', 'contact.remove-from-list', 'contact.create-period-list');
