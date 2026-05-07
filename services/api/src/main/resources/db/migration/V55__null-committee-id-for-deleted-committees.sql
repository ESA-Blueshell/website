-- Events that point at a soft-deleted Committee become orphans
-- (committee_id = NULL). Events whose committee is still live keep the
-- link — those rows are essential and must continue to resolve.
--
-- Rationale: pre-V55 the Kotlin entity declared `committee: Committee`
-- non-nullable while the DB column was always nullable (V0 created
-- `committee_id BIGINT` with no NOT NULL constraint). Some restored
-- events arrived with NULL committee_id from before the entity was
-- locked down; their `EventResponseMappings.asResponse()` NPE'd at
-- runtime. The matching entity-level relaxation lives in
-- services/api/.../domain/event/persistence/Event.kt (committee is
-- now `Committee?`).
--
-- Soft-delete sentinel: the platform uses '9999-12-31 23:59:59' for
-- "not deleted" (see CLAUDE.md "Contact soft-delete patterns" — same
-- convention applies to Committee).

UPDATE events e
JOIN   committees c ON e.committee_id = c.id
SET    e.committee_id = NULL
WHERE  c.deleted_at < '9999-12-31 23:59:59';
