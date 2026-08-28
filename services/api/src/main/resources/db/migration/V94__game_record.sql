-- Adds a game's display name and images to game_page, and enforces the link from team and
-- user_game_account to a real game.
--
-- The name was defined twice: as the `label` on the Game enum, and in a hardcoded array in the
-- frontend's index page. The accent colour, icon and banner existed only in the frontend, split
-- across two files. Adding a game therefore needed a code change and a deploy.
--
-- Only the columns and constraints are here. The values are in db/seed/esports/games.csv,
-- alongside the seasons, teams and rosters already seeded that way, so correcting a name or an
-- image is an edit to a reviewed file rather than another migration.

ALTER TABLE game_page
    ADD COLUMN name   VARCHAR(64)  NOT NULL DEFAULT '' AFTER game,
    ADD COLUMN accent VARCHAR(32)  NULL AFTER intro,
    ADD COLUMN mark   VARCHAR(255) NULL AFTER accent,
    ADD COLUMN banner VARCHAR(255) NULL AFTER mark;

-- A game's code must be unique across every row, not only among rows that are not soft-deleted:
-- teams and game accounts reference it for the life of the site. It also has to be unique on its
-- own, because a foreign key can only reference a key covering the referenced column alone.
ALTER TABLE game_page DROP INDEX uk_game_page_game;
ALTER TABLE game_page ADD UNIQUE INDEX uk_game_page_code (game);

-- Previously unenforced: the Game enum made an invalid code impossible to write in Kotlin, so
-- the database was never told about the relationship.
ALTER TABLE team
    ADD CONSTRAINT fk_team_game FOREIGN KEY (game) REFERENCES game_page (game);
ALTER TABLE user_game_account
    ADD CONSTRAINT fk_user_game_account_game FOREIGN KEY (game) REFERENCES game_page (game);
