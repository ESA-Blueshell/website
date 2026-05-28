-- Rename contact job type strings to a consistent sync / sync-all scheme.
-- Payload format is unchanged; execution history is preserved. Without this,
-- any QUEUED/RUNNING/FAILED row carrying an old type string would be marked
-- DEAD on next dispatch because no handler is registered for those names.
UPDATE job_executions SET job_type = 'contact.sync-all'              WHERE job_type = 'contact.dispatch-syncs';
UPDATE job_executions SET job_type = 'contact.list-sync-all'         WHERE job_type = 'contact.dispatch-list-syncs';
UPDATE job_executions SET job_type = 'contact.period-list-sync-all'  WHERE job_type = 'contact.ensure-period-lists';
UPDATE job_executions SET job_type = 'contact.list-sync'             WHERE job_type = 'contact.sync-list-to-system';
