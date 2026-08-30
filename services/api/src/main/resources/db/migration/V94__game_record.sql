-- What a game is called and the art it is drawn with, moved out of the frontend and into the
-- game's own row.
--
-- The name lived in a compiled enum's label and again in a table in the index page; the accent,
-- the mark and the banner lived only in the frontend, three of them in two different files. A
-- game could therefore not be added without a deploy, however complete its row was.
--
-- Only the shape is here. What each game is called and how it is drawn is in the seed files with
-- the seasons, teams and rosters, so correcting a name or an accent is an edit to a file somebody
-- can read rather than another migration.
--
-- The tie from a team and from a member's game account to its game becomes a real foreign key. It
-- was previously assumed: the enum made an unknown code unrepresentable in Kotlin and the database
-- was told nothing.

ALTER TABLE game_page
    ADD COLUMN name   VARCHAR(64)  NOT NULL DEFAULT '' AFTER game,
    ADD COLUMN accent VARCHAR(32)  NULL AFTER intro,
    ADD COLUMN mark   VARCHAR(255) NULL AFTER accent;

-- A code identifies a game for the whole life of the site, so it is unique across every row
-- rather than only among the ones still present. That is also what lets the two ties below
-- reference it: a foreign key needs a key covering the referenced column alone.
ALTER TABLE game_page DROP INDEX uk_game_page_game;
ALTER TABLE game_page ADD UNIQUE INDEX uk_game_page_code (game);

ALTER TABLE team
    ADD CONSTRAINT fk_team_game FOREIGN KEY (game) REFERENCES game_page (game);
ALTER TABLE user_game_account
    ADD CONSTRAINT fk_user_game_account_game FOREIGN KEY (game) REFERENCES game_page (game);
