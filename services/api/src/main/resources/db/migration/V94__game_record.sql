-- What a game is called and the art it is drawn with, moved out of the frontend and into the
-- game's own row.
--
-- The name lived in a compiled enum's label and again in a table in the index page; the accent,
-- the mark and the banner lived only in the frontend, three of them in two different files. A
-- game could therefore not be added without a deploy, however complete its row was.
--
-- The tie from a team and from a member's game account to its game becomes a real foreign key.
-- It was previously assumed: the enum made an unknown code unrepresentable in Kotlin and the
-- database was told nothing.

ALTER TABLE game_page
    ADD COLUMN name   VARCHAR(64)  NOT NULL DEFAULT '' AFTER game,
    ADD COLUMN accent VARCHAR(32)  NULL AFTER intro,
    ADD COLUMN mark   VARCHAR(255) NULL AFTER accent,
    ADD COLUMN banner VARCHAR(255) NULL AFTER mark;

-- Exactly the name and art the pages draw today, so nothing on screen changes. Trackmania and
-- Smash have never had an accent or a mark written for them and still do not; they read on the
-- association's own colour, which is what the frontend already falls back to.
UPDATE game_page SET name = 'Valorant',           accent = '#ff4655', mark = 'valorant.png',      banner = 'valorantesports1.jpg'    WHERE game = 'VALORANT';
UPDATE game_page SET name = 'CS2',                accent = '#e8842a', mark = 'cs2.png',           banner = 'csgoesports2.jpg'        WHERE game = 'CS2';
UPDATE game_page SET name = 'CS:GO',              accent = '#e8842a', mark = 'cs2.png',           banner = NULL                      WHERE game = 'CSGO';
UPDATE game_page SET name = 'League of Legends',  accent = '#c8963c', mark = 'league.png',        banner = 'leagueesportsbg1.jpg'    WHERE game = 'LEAGUE_OF_LEGENDS';
UPDATE game_page SET name = 'Rocket League',      accent = '#1183d6', mark = 'rocketleague.png',  banner = 'rocketleagueesports.jpg' WHERE game = 'ROCKET_LEAGUE';
UPDATE game_page SET name = 'GeoGuessr',          accent = '#6cbf3f', mark = 'geoguessrlogo.webp', banner = NULL                     WHERE game = 'GEOGUESSR';
UPDATE game_page SET name = 'Trackmania',         accent = NULL,      mark = NULL,                banner = NULL                      WHERE game = 'TRACKMANIA';
UPDATE game_page SET name = 'Super Smash Bros.',  accent = NULL,      mark = NULL,                banner = NULL                      WHERE game = 'SMASH';

ALTER TABLE game_page ALTER COLUMN name DROP DEFAULT;

-- A code identifies a game for the whole life of the site, so it is unique across every row
-- rather than only among the ones still present. That is also what lets the two ties below
-- reference it: a foreign key needs a key covering the referenced column alone.
ALTER TABLE game_page DROP INDEX uk_game_page_game;
ALTER TABLE game_page ADD UNIQUE INDEX uk_game_page_code (game);

ALTER TABLE team
    ADD CONSTRAINT fk_team_game FOREIGN KEY (game) REFERENCES game_page (game);
ALTER TABLE user_game_account
    ADD CONSTRAINT fk_user_game_account_game FOREIGN KEY (game) REFERENCES game_page (game);
