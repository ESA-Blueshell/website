-- Esports teams, the seasons they play, and who plays for them.
--
-- Rosters lived in six hand-written Vue files: a team was a name, a background image and a
-- list of `{name, ign}` with no link to a user, no dates and no table. Nothing outside the
-- page could ask who played for a team, and every roster change was a deploy.
--
-- A season is its own record rather than a contribution period, because a team can outlive a
-- period, span two of them, or run for half of one. Two seasons fall in a board year.
--
-- A roster entry's user is nullable: most of the recovered history is a handle and nothing
-- more, and a row that cannot be attributed is still worth keeping. A linked entry renders
-- the user's handle for the game from user_game_account, so a rename lands everywhere at
-- once; an unlinked one carries the handle it was published with.
--
-- That a team is fielded in a season is its own row rather than something inferred from the
-- roster. The two are decided at different times -- a team is announced when the season is
-- planned, its line-up settles over the weeks after -- and inferring one from the other made
-- the ordinary sequence impossible to record, because a team nobody was named to yet did not
-- exist in the season at all.
--
-- A roster entry hangs off that fielding rather than naming a team and a season of its own.
-- The two would otherwise say the same thing twice and could disagree: a line-up for a team
-- that is not fielded is not a state worth being able to write down, and this is what stops
-- it being written rather than a rule the services have to remember between them.

CREATE TABLE season (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(64)  NOT NULL,
    start_date    DATE         NOT NULL,
    end_date      DATE         NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_id BIGINT       NULL,
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by_id BIGINT       NULL,
    deleted_at    DATETIME(6)  NOT NULL DEFAULT '9999-12-31 23:59:59.000000',
    UNIQUE INDEX uk_season_name        (name, deleted_at),
    INDEX idx_season_dates             (start_date, end_date),
    INDEX idx_season_deleted_at        (deleted_at),
    CONSTRAINT fk_season_created FOREIGN KEY (created_by_id) REFERENCES users (id),
    CONSTRAINT fk_season_updated FOREIGN KEY (updated_by_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE team (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    game          VARCHAR(32)  NOT NULL,
    name          VARCHAR(128) NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_id BIGINT       NULL,
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by_id BIGINT       NULL,
    deleted_at    DATETIME(6)  NOT NULL DEFAULT '9999-12-31 23:59:59.000000',
    UNIQUE INDEX uk_team_game_name     (game, name, deleted_at),
    INDEX idx_team_game                (game, deleted_at),
    INDEX idx_team_deleted_at          (deleted_at),
    CONSTRAINT fk_team_created FOREIGN KEY (created_by_id) REFERENCES users (id),
    CONSTRAINT fk_team_updated FOREIGN KEY (updated_by_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE team_season (
    id            BIGINT      AUTO_INCREMENT PRIMARY KEY,
    team_id       BIGINT      NOT NULL,
    season_id     BIGINT      NOT NULL,
    version       BIGINT      NOT NULL DEFAULT 0,
    created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_id BIGINT      NULL,
    updated_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by_id BIGINT      NULL,
    deleted_at    DATETIME(6) NOT NULL DEFAULT '9999-12-31 23:59:59.000000',
    UNIQUE INDEX uk_team_season          (team_id, season_id, deleted_at),
    INDEX idx_team_season_season         (season_id, deleted_at),
    INDEX idx_team_season_deleted_at     (deleted_at),
    CONSTRAINT fk_team_season_team       FOREIGN KEY (team_id)       REFERENCES team   (id),
    CONSTRAINT fk_team_season_season     FOREIGN KEY (season_id)     REFERENCES season (id),
    CONSTRAINT fk_team_season_created    FOREIGN KEY (created_by_id) REFERENCES users  (id),
    CONSTRAINT fk_team_season_updated    FOREIGN KEY (updated_by_id) REFERENCES users  (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE team_roster_entry (
    id             BIGINT      AUTO_INCREMENT PRIMARY KEY,
    team_season_id BIGINT      NOT NULL,
    user_id       BIGINT       NULL,
    display_name  VARCHAR(128) NULL,
    handle        VARCHAR(128) NOT NULL,
    team_role     VARCHAR(16)  NOT NULL,
    sort_index    INT          NOT NULL DEFAULT 0,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_id BIGINT       NULL,
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by_id BIGINT       NULL,
    deleted_at    DATETIME(6)  NOT NULL DEFAULT '9999-12-31 23:59:59.000000',
    -- One appearance per person per fielding, whether they are named by a user link or only
    -- by the handle they were published under. The fielding already carries the team and the
    -- season, so naming them here as well would be the same fact written twice.
    UNIQUE INDEX uk_roster_entry       (team_season_id, handle, deleted_at),
    INDEX idx_roster_entry_fielding    (team_season_id, deleted_at),
    INDEX idx_roster_entry_user        (user_id, deleted_at),
    INDEX idx_roster_entry_deleted_at  (deleted_at),
    CONSTRAINT fk_roster_entry_fielding FOREIGN KEY (team_season_id) REFERENCES team_season (id),
    CONSTRAINT fk_roster_entry_user    FOREIGN KEY (user_id)       REFERENCES users  (id),
    CONSTRAINT fk_roster_entry_created FOREIGN KEY (created_by_id) REFERENCES users  (id),
    CONSTRAINT fk_roster_entry_updated FOREIGN KEY (updated_by_id) REFERENCES users  (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_game_account (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    game          VARCHAR(32)  NOT NULL,
    handle        VARCHAR(128) NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_id BIGINT       NULL,
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by_id BIGINT       NULL,
    deleted_at    DATETIME(6)  NOT NULL DEFAULT '9999-12-31 23:59:59.000000',
    UNIQUE INDEX uk_user_game_account  (user_id, game, deleted_at),
    INDEX idx_user_game_account_game   (game, deleted_at),
    INDEX idx_user_game_account_del    (deleted_at),
    CONSTRAINT fk_user_game_account_user    FOREIGN KEY (user_id)       REFERENCES users (id),
    CONSTRAINT fk_user_game_account_created FOREIGN KEY (created_by_id) REFERENCES users (id),
    CONSTRAINT fk_user_game_account_updated FOREIGN KEY (updated_by_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
