-- Retire the LISTMONK value of the ContactSystem enum.
--
-- Hibernate maps `ContactSystem` as STRING — any row that still carries
-- `system = 'LISTMONK'` (or a queued/failed job payload referencing the
-- old enum) would throw `IllegalArgumentException: No enum constant
-- ContactSystem.LISTMONK` the first time the ORM or Jackson tries to
-- deserialise it.

-- ── contact_external_ids ──────────────────────────────────────────────
-- Listmonk subscriber IDs are no longer reachable; the corresponding
-- Brevo row (if any) for the same contact remains untouched.
DELETE FROM contact_external_ids WHERE system = 'LISTMONK';

-- ── contact_list_external_ids ────────────────────────────────────────
-- Same shape: drop the Listmonk-side list IDs, keep Brevo's.
DELETE FROM contact_list_external_ids WHERE system = 'LISTMONK';

-- ── job_executions ───────────────────────────────────────────────────
-- Queued or replayable per-system contact sync jobs whose payload still
-- references `"system":"LISTMONK"`. The payload format is unchanged but
-- the enum value is gone, so Jackson would fail at handler dispatch.
-- Mark them DEAD with the same shape PR #58's migration used for the
-- earlier `brevo.*`/`listmonk.*` retirement.
UPDATE job_executions
SET status       = 'DEAD',
    error_type   = 'HandlerRemoved',
    error_reason = 'Listmonk integration retired — ContactSystem.LISTMONK no longer exists'
WHERE job_type IN ('contact.sync-to-system', 'contact.sync-list-to-system')
  AND status IN ('QUEUED', 'RUNNING', 'FAILED')
  AND payload LIKE '%"system":"LISTMONK"%';
