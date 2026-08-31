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

ALTER TABLE game
    ADD COLUMN banner_file_id BIGINT NULL AFTER accent,
    ADD COLUMN icon_file_id   BIGINT NULL AFTER banner_file_id,
    ADD CONSTRAINT fk_game_banner FOREIGN KEY (banner_file_id) REFERENCES files (id),
    ADD CONSTRAINT fk_game_icon FOREIGN KEY (icon_file_id) REFERENCES files (id);

-- A team's two pictures have two different lifetimes, so they sit on two different rows. The
-- icon is the logo beside its name and belongs to the team: it is who they are, and it is the
-- one thing that ties BS HyperS on a CS:GO page to BS HyperS on a CS2 one. The banner is the
-- large art behind the slice and belongs to the fielding, because it is game-flavoured -- the
-- same team keeps its CS:GO art on CS:GO seasons and its CS2 art on CS2 seasons -- and because
-- a team fielded again carries the last one across rather than being asked for it every season.
ALTER TABLE team
    ADD COLUMN icon_file_id   BIGINT NULL AFTER name,
    ADD CONSTRAINT fk_team_icon FOREIGN KEY (icon_file_id) REFERENCES files (id);

ALTER TABLE team_season
    ADD COLUMN banner_file_id BIGINT NULL AFTER season_id,
    ADD CONSTRAINT fk_team_season_banner FOREIGN KEY (banner_file_id) REFERENCES files (id);

ALTER TABLE team_roster_entry
    ADD COLUMN icon_file_id BIGINT NULL AFTER description,
    ADD CONSTRAINT fk_roster_entry_icon FOREIGN KEY (icon_file_id) REFERENCES files (id);
