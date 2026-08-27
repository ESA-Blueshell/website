package net.blueshell.api.domain.esports.persistence.repository

import net.blueshell.api.domain.esports.persistence.TeamSeason
import net.blueshell.api.shared.enums.Game
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface TeamSeasonRepository : JpaRepository<TeamSeason, Long> {
    /**
     * Every team a game fielded in one season, the team fetched with it: the page draws them
     * all at once and lazy loading would be a query per team.
     */
    @Query(
        """
        SELECT ts FROM TeamSeason ts
        JOIN FETCH ts.team t
        WHERE t.game = :game AND ts.season.id = :seasonId
        ORDER BY t.name ASC
        """,
    )
    fun findAllByGameAndSeason(
        @Param("game") game: Game,
        @Param("seasonId") seasonId: Long,
    ): List<TeamSeason>

    /** The seasons a game fielded a team in, newest first, for the page's season switcher. */
    @Query(
        """
        SELECT DISTINCT s.id FROM TeamSeason ts
        JOIN ts.team t
        JOIN ts.season s
        WHERE t.game = :game
        ORDER BY s.id DESC
        """,
    )
    fun findSeasonIdsFielded(@Param("game") game: Game): List<Long>

    fun findByTeamIdAndSeasonId(teamId: Long, seasonId: Long): TeamSeason?

    /** The seasons one team was fielded in, newest first. */
    @Query("SELECT ts FROM TeamSeason ts JOIN FETCH ts.season s WHERE ts.team.id = :teamId ORDER BY s.startDate DESC")
    fun findAllByTeamId(@Param("teamId") teamId: Long): List<TeamSeason>
}
