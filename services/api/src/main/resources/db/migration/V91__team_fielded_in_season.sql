-- Which teams the association fielded in which season.
--
-- Until now this was inferred: a team belonged to a season because somebody had a roster
-- entry there. That made the ordinary sequence impossible to record — decide to field a team,
-- announce it, settle the line-up over the following weeks — because a team nobody was on yet
-- did not exist in the season at all.
--
-- The link is its own fact now. Every link the roster entries already imply is written across,
-- so nothing that renders today changes.

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

-- Every team that has somebody on it in a season was fielded in that season.
INSERT INTO team_season (team_id, season_id)
SELECT DISTINCT e.team_id, e.season_id
FROM team_roster_entry e
WHERE e.deleted_at = '9999-12-31 23:59:59.000000';
