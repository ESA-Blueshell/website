-- How a game presents itself: the address it answers to, what is said about it, where it sits
-- in the list, and whether the association still fields a team in it.
--
-- All four lived in the frontend until now. Six components differed only by a paragraph, the
-- addresses were hand-written in the router with no relation to the game they served, and two
-- games the association fielded teams in had no page at all. Trackmania had a component with
-- copy written for it and no route, so it has been unreachable.
--
-- The enum stays what a team, a game account and the cohort rules refer to. This is only how a
-- game is presented.

CREATE TABLE game (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    code          VARCHAR(32)  NOT NULL,
    slug          VARCHAR(64)  NOT NULL,
    intro         TEXT         NULL,
    sort_index    INT          NOT NULL DEFAULT 0,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_id BIGINT       NULL,
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by_id BIGINT       NULL,
    deleted_at    DATETIME(6)  NOT NULL DEFAULT '9999-12-31 23:59:59.000000',
    UNIQUE INDEX uk_game_code_active  (code, deleted_at),
    UNIQUE INDEX uk_game_slug         (slug, deleted_at),
    INDEX idx_game_deleted_at         (deleted_at),
    CONSTRAINT fk_game_created FOREIGN KEY (created_by_id) REFERENCES users (id),
    CONSTRAINT fk_game_updated FOREIGN KEY (updated_by_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- The slugs are the addresses the router already answers to, so every link that exists keeps
-- working. The copy is what each page already said. Trackmania is given the address it never
-- had. Whether the association still plays one is not recorded: it is derived from the
-- seasons, where a team playing it is what says so.
INSERT INTO game (code, slug, sort_index, intro) VALUES
('VALORANT', 'valorant', 1,
 'With shooters'' prevalence in the global esports scene, not only do we have a CS team, but also multiple Valorant teams. They''ll be battling it out within the dutch Valorant esports scene. Below you can find our competitive Valorant teams.'),
('CS2', 'counter-strike-2', 2,
 'With shooters'' prevalence in the global esports scene, Blueshell Esports''s CS2 teams are trying to climb up the charts with those sweet headshots! Below you can find our competitive CS2 team(s).'),
('LEAGUE_OF_LEGENDS', 'league-of-legends', 3,
 'As it is around the globe, League of Legends as a competitive ground holds a special place in Blueshell Esports. Below you can find our competitive League of Legends team(s).'),
('ROCKET_LEAGUE', 'rocketleague', 4,
 'This year we have another new game Blueshell will compete in. A new rocket league team has been created! They will be joining tournaments representing Blueshell and compete in DSL.'),
('GEOGUESSR', 'geoguessr', 5,
 'Geoguessr is one of Blueshell Esports'' newest competitive titles. Our players test their geographical knowledge and quick decision-making in community events and tournaments, proudly representing Blueshell esports.'),
('TRACKMANIA', 'trackmania', 6,
 'We have two players playing under the Blueshell banner for multiple Trackmania tournaments, most notably in the regionals of the Trackmania World Tour circuit. Aside from that we have multiple players sporting our club tag, competing in whatever tournament happens to be active.'),
('CSGO', 'counter-strike-global-offensive', 7, NULL),
('SMASH', 'super-smash-bros', 8, NULL);
