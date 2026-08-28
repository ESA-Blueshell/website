-- Posters, banners and player icons as uploads rather than files bundled into the frontend.
--
-- Every image on the esports pages is a file in the frontend's assets directory named by a
-- string in `team.image`. Adding a picture is a deploy, a team fielded since the last release
-- has none at all, and the banner behind every page is one hardcoded file for the whole site.
--
-- A poster hangs off the team and an icon off the roster entry, so a player can look
-- different across seasons and an entry with no account attached can still have one. A banner
-- is its own record: it names a game always, and narrows that to a season, a team, or both.
--
-- `team.image` is left alone. The bundled images keep working while anything still references
-- them, and a page falls back to one until a poster is uploaded over it.

ALTER TABLE team
    ADD COLUMN poster_file_id BIGINT NULL AFTER image,
    ADD CONSTRAINT fk_team_poster FOREIGN KEY (poster_file_id) REFERENCES files (id);

ALTER TABLE team_roster_entry
    ADD COLUMN icon_file_id BIGINT NULL AFTER description,
    ADD CONSTRAINT fk_roster_entry_icon FOREIGN KEY (icon_file_id) REFERENCES files (id);

-- One banner per combination of game, season and team, where the last two are optional.
--
-- The obvious UNIQUE (game, season_id, team_id, deleted_at) does not hold: MariaDB counts
-- NULLs as distinct, so it admits any number of game-wide banners, which is the case most
-- worth constraining. `scope_key` folds the two optional columns onto a value that is never
-- null, and the index is on that instead. It is written by the database and never mapped.
CREATE TABLE esports_banner (
    id            BIGINT      AUTO_INCREMENT PRIMARY KEY,
    game          VARCHAR(32) NOT NULL,
    season_id     BIGINT      NULL,
    team_id       BIGINT      NULL,
    file_id       BIGINT      NOT NULL,
    version       BIGINT      NOT NULL DEFAULT 0,
    created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_id BIGINT      NULL,
    updated_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by_id BIGINT      NULL,
    deleted_at    DATETIME(6) NOT NULL DEFAULT '9999-12-31 23:59:59.000000',
    scope_key     VARCHAR(96) AS (CONCAT(game, '|', COALESCE(season_id, 0), '|', COALESCE(team_id, 0))) STORED,
    UNIQUE INDEX uk_esports_banner_scope       (scope_key, deleted_at),
    INDEX idx_esports_banner_game              (game, deleted_at),
    INDEX idx_esports_banner_deleted_at        (deleted_at),
    CONSTRAINT fk_esports_banner_season  FOREIGN KEY (season_id)     REFERENCES season (id),
    CONSTRAINT fk_esports_banner_team    FOREIGN KEY (team_id)       REFERENCES team   (id),
    CONSTRAINT fk_esports_banner_file    FOREIGN KEY (file_id)       REFERENCES files  (id),
    CONSTRAINT fk_esports_banner_created FOREIGN KEY (created_by_id) REFERENCES users  (id),
    CONSTRAINT fk_esports_banner_updated FOREIGN KEY (updated_by_id) REFERENCES users  (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
