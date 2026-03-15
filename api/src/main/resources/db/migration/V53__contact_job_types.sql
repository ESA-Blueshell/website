-- Kill QUEUED/RUNNING/FAILED records for retired per-system job types.
-- The old payload format {"userId":X} is incompatible with the new {"userId":X,"system":"..."}
-- format so these cannot be transparently replayed.
UPDATE job_executions
SET status       = 'DEAD',
    error_type   = 'HandlerRemoved',
    error_reason = 'Job type retired (contact sync architecture refactor — see ContactJobs.SyncContactToSystem)'
WHERE job_type IN ('brevo.contact.sync', 'listmonk.contact.sync', 'brevo.list.sync', 'listmonk.list.sync')
  AND status IN ('QUEUED', 'RUNNING', 'FAILED');

-- Rename contact job type strings to match the refactored ContactJobs definitions.
-- Payload format is unchanged; execution history is fully preserved.
UPDATE job_executions SET job_type = 'contact.dispatch-syncs'          WHERE job_type = 'contact.spawn-syncs';
UPDATE job_executions SET job_type = 'contact.dispatch-list-syncs'     WHERE job_type = 'contact.spawn-list-syncs';
UPDATE job_executions SET job_type = 'contact.process-list-membership' WHERE job_type = 'contact.sync-list-membership';
UPDATE job_executions SET job_type = 'contact.sync-to-system'          WHERE job_type = 'contact.sync-for-system';
UPDATE job_executions SET job_type = 'contact.sync-list-to-system'     WHERE job_type = 'contact.list-sync-for-system';
