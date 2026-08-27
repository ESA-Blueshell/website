package net.blueshell.api.domain.esports.persistence.repository

import net.blueshell.api.domain.esports.persistence.TeamRosterEntry
import net.blueshell.api.shared.enums.Game
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface TeamRosterEntryRepository : JpaRepository<TeamRosterEntry, Long> {
    /**
     * A whole game's rosters for one season, teams and seasons fetched with them: the page
     * draws every team at once, and lazy loading them would be one query per team.
     */
    @Query(
        """
        SELECT e FROM TeamRosterEntry e
        JOIN FETCH e.team t
        JOIN FETCH e.season s
        WHERE t.game = :game AND s.id = :seasonId
        ORDER BY t.name ASC, e.teamRole ASC, e.sortIndex ASC
        """,
    )
    fun findAllByGameAndSeason(
        @Param("game") game: Game,
        @Param("seasonId") seasonId: Long,
    ): List<TeamRosterEntry>

    @Query(
        """
        SELECT e FROM TeamRosterEntry e
        JOIN FETCH e.team t
        JOIN FETCH e.season s
        WHERE t.id = :teamId AND s.id = :seasonId
        ORDER BY e.teamRole ASC, e.sortIndex ASC
        """,
    )
    fun findAllByTeamAndSeason(
        @Param("teamId") teamId: Long,
        @Param("seasonId") seasonId: Long,
    ): List<TeamRosterEntry>

    /** The seasons a game has rosters for, newest first, for the page's season switcher. */
    @Query(
        """
        SELECT DISTINCT s.id FROM TeamRosterEntry e
        JOIN e.team t
        JOIN e.season s
        WHERE t.game = :game
        ORDER BY s.id DESC
        """,
    )
    fun findSeasonIdsWithRosters(@Param("game") game: Game): List<Long>

    /**
     * Whether a member held a roster spot in a season overlapping the window — the question
     * "was this user active in that year" reduces to, asked of one user.
     */
    @Query(
        """
        SELECT COUNT(e) > 0 FROM TeamRosterEntry e
        JOIN e.season s
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
        JOIN e.season s
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
        JOIN e.season s
        WHERE e.team.id = :teamId AND s.id <> :ignoring
        GROUP BY s.id, s.startDate
        ORDER BY s.startDate DESC
        """,
    )
    fun findSeasonIdsWithLineup(
        @Param("teamId") teamId: Long,
        @Param("ignoring") ignoring: Long,
    ): List<Long>

    fun findAllByUserId(userId: Long): List<TeamRosterEntry>
}
