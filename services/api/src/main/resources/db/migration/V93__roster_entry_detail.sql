-- A roster entry says whether somebody was a player, a substitute or a coach, and nothing
-- else. That enum is the shape of the squad, not the shape of the person in it: a captain, an
-- in-game leader and a jungler are all PLAYER. Both columns are optional and belong to the
-- entry, so they vary per season the way the rest of it does.
ALTER TABLE team_roster_entry
    ADD COLUMN role_title VARCHAR(64) NULL AFTER team_role,
    ADD COLUMN description VARCHAR(280) NULL AFTER role_title;
