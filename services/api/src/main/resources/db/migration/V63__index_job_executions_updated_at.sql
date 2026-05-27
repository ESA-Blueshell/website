-- Order the job-management list by most-recent activity.
--
-- The admin jobs page now pages by updated_at DESC so the last-run job sits at
-- the top and a manual retry (which bumps updated_at) jumps straight there.
-- Index updated_at to keep that ordered, paginated scan cheap.

CREATE INDEX idx_job_executions_updated_at ON job_executions (updated_at);
