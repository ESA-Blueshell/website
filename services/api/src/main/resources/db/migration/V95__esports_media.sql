-- The pictures on the esports pages are uploads rather than files bundled into the frontend.
--
-- Every image on these pages was a file in the frontend's assets directory named by a string on
-- the row. Adding a picture was a deploy, a team fielded since the last release had none at all,
-- and the image behind every page was one hardcoded file for the whole site.
--
-- Two pictures per record, and both are called the same thing whichever record carries them. A
-- banner is the large one behind a diagonal slice: a game's is drawn in the slice for it on the
-- index, a team's in the slice for it on its game's page. An icon is the small one identifying
-- the thing itself, drawn in that same slice beside the title. Neither page draws an image
-- anywhere else -- the headers are the accent and nothing more -- so there is nothing for a
-- picture to be resolved against and no reason for a banner to be a record of its own.
--
-- A roster entry's icon sits beside them, so a player can look different across seasons and an
-- entry with no account attached can still have a face.

ALTER TABLE game_page
    ADD COLUMN banner_file_id BIGINT NULL AFTER accent,
    ADD COLUMN icon_file_id   BIGINT NULL AFTER banner_file_id,
    ADD CONSTRAINT fk_game_page_banner FOREIGN KEY (banner_file_id) REFERENCES files (id),
    ADD CONSTRAINT fk_game_page_icon FOREIGN KEY (icon_file_id) REFERENCES files (id);

ALTER TABLE team
    ADD COLUMN banner_file_id BIGINT NULL AFTER name,
    ADD COLUMN icon_file_id   BIGINT NULL AFTER banner_file_id,
    ADD CONSTRAINT fk_team_banner FOREIGN KEY (banner_file_id) REFERENCES files (id),
    ADD CONSTRAINT fk_team_icon FOREIGN KEY (icon_file_id) REFERENCES files (id);

ALTER TABLE team_roster_entry
    ADD COLUMN icon_file_id BIGINT NULL AFTER description,
    ADD CONSTRAINT fk_roster_entry_icon FOREIGN KEY (icon_file_id) REFERENCES files (id);
