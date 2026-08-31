-- A payment request becomes one row per ask.
--
-- The treasurer chases. A member can be asked in September, again in February and again the
-- week after, and each of those is a thing that happened. Only one constraint ever forbade
-- that: the unique key over (user, period, deleted_at).
--
-- The table has carried an `id` primary key since V30. The entity described it with a
-- composite `@EmbeddedId` over (user_id, contribution_period_id) instead, which the unique
-- key made behave like a primary key — so the mapping and the schema disagreed, and dropping
-- the constraint is what lets the entity say what the table has always been.
ALTER TABLE contribution_reminders
    DROP CONSTRAINT uk_contribution_reminders_user_period_deleted_at;

-- The pair is still how the cycle reads a member's asks, and now the newest of them.
CREATE INDEX idx_contribution_reminders_user_period_asked
    ON contribution_reminders (user_id, contribution_period_id, asked_at);
