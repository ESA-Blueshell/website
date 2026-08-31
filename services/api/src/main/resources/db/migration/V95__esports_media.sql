-- The esports pictures are uploads rather than files bundled into the frontend.
--
-- Every one of them was a file in the frontend's assets directory named by a string on the row.
-- Adding a picture was a deploy, a team fielded since the last release had none at all, and the
-- art behind the whole section was one hardcoded file.
--
-- Two pictures per record, and both are called the same thing whichever record carries them. A
-- banner is the large one a record is drawn on: a game's where it is listed among the others, a
-- team's where it is listed under its game. An icon is the small one identifying the thing
-- itself, drawn there beside the title. Neither is drawn anywhere else, so there is nothing for
-- a picture to be resolved against and no reason for a banner to be a record of its own.
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
-- one thing that ties BS HyperS in CS:GO to BS HyperS in CS2. The banner is the large art the
-- team is drawn on and belongs to the fielding, because it is game-flavoured -- the
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
