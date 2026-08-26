-- Whether a member's real name may appear beside their handle on the team pages.
--
-- Names came off those pages deliberately, and the roster import put them back in the
-- database for identification rather than for publication. Some members do want theirs
-- shown, and that is their decision to make, not the association's to make for them.
--
-- Default false, for the members who already exist as much as for the ones who follow: the
-- import publishes nothing until somebody says otherwise.
ALTER TABLE member_profiles
    ADD COLUMN name_on_team_pages BOOLEAN NOT NULL DEFAULT FALSE;
