-- What a game is called and the art it is drawn with, moved out of the frontend and into the
-- game's own row.
--
-- The name lived in a compiled enum's label and again in a table in the frontend; the accent,
-- the icon and the banner lived only in the frontend, three of them in two different files. A
-- game could therefore not be added without a deploy, however complete its row was.
--
-- The pictures are not here. Both of a game's are uploads addressed by their contents, so they
-- are file references established with the rest of the esports media rather than names in this
-- table.
--
-- Only the shape is here. What each game is called and how it is drawn is in the seed files with
-- the seasons, teams and rosters, so correcting a name or an accent is an edit to a file somebody
-- can read rather than another migration.
--
-- The tie from a fielding and from a member's game account to its game becomes a real foreign key. It
-- was previously assumed: the enum made an unknown code unrepresentable in Kotlin and the database
-- was told nothing.

ALTER TABLE game
    ADD COLUMN name   VARCHAR(64) NOT NULL DEFAULT '' AFTER code,
    ADD COLUMN accent VARCHAR(32) NULL AFTER intro;

-- A code identifies a game for the whole life of the site, so it is unique across every row
-- rather than only among the ones still present. That is also what lets the two ties below
-- reference it: a foreign key needs a key covering the referenced column alone.
ALTER TABLE game DROP INDEX uk_game_code_active;
ALTER TABLE game ADD UNIQUE INDEX uk_game_code (code);

-- On the fielding rather than on the team: a team is the association's and plays whatever games
-- it plays, so the game it played is a fact about being fielded.
ALTER TABLE team_season
    ADD CONSTRAINT fk_team_season_game FOREIGN KEY (game) REFERENCES game (code);
ALTER TABLE season_game
    ADD CONSTRAINT fk_season_game_game FOREIGN KEY (game) REFERENCES game (code);
ALTER TABLE user_game_account
    ADD CONSTRAINT fk_user_game_account_game FOREIGN KEY (game) REFERENCES game (code);
