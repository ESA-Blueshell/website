package net.blueshell.api.esports.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface TeamRosterEntryRepository : JpaRepository<TeamRosterEntry, Long> {
    /**
     * A whole game's rosters for one season, the fielding and what it names fetched with them:
     * one read answers with every team at once, and lazy loading them would be one query per team.
     */
    @Query(
        """
        SELECT e FROM TeamRosterEntry e
        JOIN FETCH e.teamSeason ts
        JOIN FETCH ts.team t
        JOIN FETCH ts.season s
        LEFT JOIN FETCH e.icon
        WHERE ts.game = :game AND s.id = :seasonId
        ORDER BY t.name ASC, e.teamRole ASC, e.sortIndex ASC
        """,
    )
    fun findAllByGameAndSeason(
        @Param("game") game: String,
        @Param("seasonId") seasonId: Long,
    ): List<TeamRosterEntry>

    /** One team's line-up, the icons fetched with it for the same reason as the whole game's. */
    @Query(
        """
        SELECT e FROM TeamRosterEntry e
        JOIN FETCH e.teamSeason ts
        JOIN FETCH ts.team t
        JOIN FETCH ts.season s
        LEFT JOIN FETCH e.icon
        WHERE t.id = :teamId AND ts.game = :game AND s.id = :seasonId
        ORDER BY e.teamRole ASC, e.sortIndex ASC
        """,
    )
    fun findAllByTeamAndSeason(
        @Param("teamId") teamId: Long,
        @Param("game") game: String,
        @Param("seasonId") seasonId: Long,
    ): List<TeamRosterEntry>

    /** The seasons a game has rosters for, newest first, for choosing among them. */
    @Query(
        """
        SELECT DISTINCT s.id FROM TeamRosterEntry e
        JOIN e.teamSeason ts
        JOIN ts.season s
        WHERE ts.game = :game
        ORDER BY s.id DESC
        """,
    )
    fun findSeasonIdsWithRosters(@Param("game") game: String): List<Long>

    /**
     * Whether a member held a roster spot in a season overlapping the window — the question
     * "was this user active in that year" reduces to, asked of one user.
     */
    @Query(
        """
        SELECT COUNT(e) > 0 FROM TeamRosterEntry e
        JOIN e.teamSeason ts
        JOIN ts.season s
        WHERE e.userId = :userId AND s.startDate <= :to AND s.endDate >= :from
        """,
    )
    fun existsForUserInWindow(
        @Param("userId") userId: Long,
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
    ): Boolean

    /** Every member who held a roster spot in a season overlapping the window. */
    @Query(
        """
        SELECT DISTINCT e.userId FROM TeamRosterEntry e
        JOIN e.teamSeason ts
        JOIN ts.season s
        WHERE e.userId IS NOT NULL AND s.startDate <= :to AND s.endDate >= :from
        """,
    )
    fun findUserIdsInWindow(@Param("from") from: LocalDate, @Param("to") to: LocalDate): List<Long>

    /**
     * The seasons a team has a line-up for, newest first, ignoring one. Carrying a line-up
     * across asks for the first of these, and must not answer with the season being filled.
     */
    @Query(
        """
        SELECT s.id FROM TeamRosterEntry e
        JOIN e.teamSeason ts
        JOIN ts.season s
        WHERE ts.team.id = :teamId AND ts.game = :game AND s.id <> :ignoring
        GROUP BY s.id, s.startDate
        ORDER BY s.startDate DESC
        """,
    )
    fun findSeasonIdsWithLineup(
        @Param("teamId") teamId: Long,
        @Param("game") game: String,
        @Param("ignoring") ignoring: Long,
    ): List<Long>

    /**
     * People on the line-ups of one season, for a removal to say before it happens.
     *
     * Native, as are the two below it: a line-up outlives the dropping of the team that played
     * it, so the entries stay while the fielding is soft-deleted, and a Hibernate join through
     * it would drop exactly those rows from the count.
     */
    @Query(
        nativeQuery = true,
        value = """
        SELECT COUNT(*) FROM team_roster_entry e
        JOIN team_season ts ON ts.id = e.team_season_id
        WHERE ts.season_id = :seasonId AND e.deleted_at = '9999-12-31 23:59:59.000000'
        """,
    )
    fun countBySeasonId(@Param("seasonId") seasonId: Long): Long

    /** People on the line-ups of every team of one game, for a removal to say before it happens. */
    @Query(
        nativeQuery = true,
        value = """
        SELECT COUNT(*) FROM team_roster_entry e
        JOIN team_season ts ON ts.id = e.team_season_id
        JOIN team t ON t.id = ts.team_id
        WHERE ts.game = :game
          AND e.deleted_at = '9999-12-31 23:59:59.000000'
          AND t.deleted_at = '9999-12-31 23:59:59.000000'
        """,
    )
    fun countByGame(@Param("game") game: String): Long
}
