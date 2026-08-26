-- A board seat that belongs to nobody with an account.
--
-- Nine boards sat in a Vue file: names, titles, personal blurbs and photographs, none of it
-- reachable by anything that asks who was on a board. Most of those people never had an
-- account here, and `board_members.user_id` was NOT NULL, so the history could not be stored
-- at all. The table also had nowhere to put the blurb the page shows.
--
-- The surrogate key the entity is about to use already exists: `id` has been the primary key
-- in the database all along, while the entity mapped its identity to (board_id, user_id).
-- Only the columns change here.
--
-- `image` holds an asset file name rather than a `files` row, the same way a team's image
-- does: these portraits ship with the frontend and are referenced, not uploaded.

ALTER TABLE board_members
    MODIFY COLUMN user_id BIGINT NULL,
    ADD COLUMN display_name VARCHAR(128) NULL AFTER user_id,
    ADD COLUMN description TEXT NULL AFTER display_name,
    ADD COLUMN image VARCHAR(255) NULL AFTER description;

ALTER TABLE boards
    ADD COLUMN image VARCHAR(255) NULL AFTER picture_id;

-- The unique key stays as it is: MariaDB lets a nullable column repeat NULL inside a unique
-- index, so any number of seats with no account can sit on one board while a member still
-- cannot hold the same seat twice.
