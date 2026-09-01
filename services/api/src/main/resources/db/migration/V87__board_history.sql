-- What a board is, beyond a name and two dates.
--
-- A board is one year of the association's life, and the page that lists them had none of
-- what says so: not the board's place in the line, not the name it chose for itself, not the
-- line it shouted, not its colour, and nowhere to write what the year was about.
--
-- Dating comes from the site's own history rather than from the page, which carries none:
-- the site said "goodbye board 5, hello board 6" in October 2022, was created showing the
-- sitting board in October 2020, and gained the 9th board in October 2025. One board a year,
-- changing in the autumn, so board N runs the association year beginning September 2016 + N.
-- The day of the handover was not recorded for any board before the ninth, so for those the
-- year is the unit; the ninth board's own handover, on 16 September 2026, is the first that is
-- actually known, and the tenth takes office the day after it.
--
-- Whether a board is in office or is still a candidate is read off those dates rather than
-- flagged: a board is a candidate while its start date is in the future, and in office while
-- today falls inside its term. No column says either, so no column can disagree with them.
--
-- Only the shape is here. The boards and their seats live in `db/seed/boards`, loaded by a
-- repeatable migration keyed on those files' own contents, so correcting a name somebody
-- misremembered is an edit to a file rather than another migration.
--
-- `number` is the board's ordinal and replaces the name as its identity: a name is a thing a
-- board chose and may not have been recorded, while its place in the line always is.
-- `candidate` is untouched by decision -- it duplicates the name and nothing reads it, and
-- dropping it is a separate change.
--
-- `image` on both tables stays: those photographs still ship with the frontend, and the page
-- that draws them keeps working until pictures become uploads.

ALTER TABLE boards
    ADD COLUMN number      INT          NOT NULL AFTER id,
    ADD COLUMN cheer       VARCHAR(255) NULL     AFTER name,
    ADD COLUMN accent      VARCHAR(32)  NULL     AFTER cheer,
    ADD COLUMN description TEXT         NULL     AFTER accent,
    MODIFY COLUMN name VARCHAR(255) NULL;

-- Whatever rows a database already holds are numbered by their own key, so the unique key
-- below can be built at all. None of them is the association's history: that comes from the
-- seed files, which is also why a database that ran an earlier form of this migration is
-- dropped and reseeded rather than repaired.
UPDATE boards SET number = id WHERE number = 0;

-- The identity moves from the name and the start date to the number, which is unique among
-- the boards that exist. Deleted rows keep their own numbers out of the way through
-- `deleted_at`, the same way every other key in this schema does.
ALTER TABLE boards DROP INDEX uk_boards_name_start_date_deleted_at;
ALTER TABLE boards ADD CONSTRAINT uk_boards_number_deleted_at UNIQUE (number, deleted_at);

-- Most of this history is written in nicknames: `Roos "SkyeWolf" Kruk` was one string, and
-- nothing could ask for the name without the quotes in the middle of it. The nickname belongs
-- to the seat rather than to the member, the way a roster entry's handle does, because the
-- same person may be known by different names in different years.
ALTER TABLE board_members
    ADD COLUMN nickname VARCHAR(128) NULL AFTER display_name;
