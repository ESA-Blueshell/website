-- Retire legacy per-system job type strings added before the contact sync architecture refactor.
-- The handlers for these types have been removed; they are replaced by the unified
-- 'contact.sync-for-system' and 'contact.list-sync-for-system' types.
--
-- The old payload format {"userId":X} is incompatible with the new {"userId":X,"system":"..."}
-- format, so QUEUED/FAILED records cannot be migrated transparently — mark them DEAD.
-- SUCCESS/DEAD records are preserved for audit purposes.
UPDATE job_executions
SET status       = 'DEAD',
    error_type   = 'HandlerRemoved',
    error_reason = 'Job type retired in V60 (contact sync architecture refactor — see ContactJobs.SyncContactForSystem)'
WHERE job_type IN (
    'brevo.contact.sync',
    'listmonk.contact.sync',
    'brevo.list.sync',
    'listmonk.list.sync'
)
  AND status IN ('QUEUED', 'RUNNING', 'FAILED');
