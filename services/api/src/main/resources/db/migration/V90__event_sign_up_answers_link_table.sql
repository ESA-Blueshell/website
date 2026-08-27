-- `event_sign_up_answers` is a link table, and its columns now say so.
--
-- The table had two Hibernate mappings that disagreed: a plain join table under the sign-up's
-- answer collection, and a soft-delete entity with a composite id, its own delete statement and
-- an optimistic-lock column. The entity had no repository and no service, so nothing ever wrote
-- through it. The join table is the mapping that survives: an answer belongs to exactly one
-- sign-up, the answer row itself carries the soft-delete that hides it, and a withdrawn sign-up
-- is recovered through `event_signups`, not through its links.
--
-- `version` was never anything but 0 — only the entity mapped it, and the entity was never
-- persisted outside tests. `created_by_id` and `updated_by_id` came from the same mapping and
-- are null on every row for the same reason. All three go, along with the two foreign keys the
-- audit columns carried into `users`.
--
-- `deleted_at` stays, and is deliberately left unmapped. It is not empty: V24 seeded it from
-- `event_signups.deleted_at`, so links belonging to sign-ups withdrawn before the survey
-- migration hold a real timestamp. It is also a column of both unique keys, so dropping it
-- would narrow `uk_event_sign_up_answers_answer_deleted_at` to `answer_id` alone and fail on
-- any historical pair that repeats across a deletion. Those rows are already invisible twice
-- over — the sign-up that owns them is soft-deleted, and so is the answer they point at.
--
-- `id` stays for the same kind of reason: it is the primary key, and the pair
-- (event_sign_up_id, answer_id) cannot replace it while soft-deleted duplicates of a pair are
-- permitted by the unique key above. `created_at` and `updated_at` stay because the database
-- fills them from their defaults and they record when the link was made.
--
-- The pre-survey payload columns (`question_id`, `option_selections`, `text_response`) are not
-- mentioned here: V24 already dropped them once the payload moved to `answers`.

ALTER TABLE event_sign_up_answers
    DROP FOREIGN KEY fk_event_sign_up_answers_on_created_by;

ALTER TABLE event_sign_up_answers
    DROP FOREIGN KEY fk_event_sign_up_answers_on_updated_by;

ALTER TABLE event_sign_up_answers
    DROP COLUMN created_by_id,
    DROP COLUMN updated_by_id,
    DROP COLUMN version;
