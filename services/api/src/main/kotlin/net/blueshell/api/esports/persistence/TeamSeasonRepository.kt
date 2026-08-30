package net.blueshell.api.esports.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
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
        LEFT JOIN FETCH ts.banner
        LEFT JOIN FETCH t.icon
        WHERE ts.game = :game AND ts.season.id = :seasonId
        ORDER BY t.name ASC
        """,
    )
    fun findAllByGameAndSeason(
        @Param("game") game: String,
        @Param("seasonId") seasonId: Long,
    ): List<TeamSeason>

    /** The seasons a game fielded a team in, newest first, for the page's season switcher. */
    @Query(
        """
        SELECT DISTINCT s.id FROM TeamSeason ts
        JOIN ts.season s
        WHERE ts.game = :game
        ORDER BY s.id DESC
        """,
    )
    fun findSeasonIdsFielded(@Param("game") game: String): List<Long>

    fun findByTeamIdAndGameAndSeasonId(teamId: Long, game: String, seasonId: Long): TeamSeason?

    fun countBySeasonId(seasonId: Long): Long

    /** The seasons that had a team fielded in them, whichever game it played. */
    @Query("SELECT DISTINCT ts.season.id FROM TeamSeason ts")
    fun seasonIdsWithTeams(): List<Long>

    /** The games a team was fielded in across any of these seasons. */
    @Query("SELECT DISTINCT ts.game FROM TeamSeason ts WHERE ts.season.id IN :seasonIds")
    fun gamesFieldedIn(@Param("seasonIds") seasonIds: Collection<Long>): List<String>

    /** The seasons one team was fielded in, newest first. */
    @Query("SELECT ts FROM TeamSeason ts JOIN FETCH ts.season s WHERE ts.team.id = :teamId ORDER BY s.startDate DESC")
    fun findAllByTeamId(@Param("teamId") teamId: Long): List<TeamSeason>

    /**
     * How many of the association's teams have ever been fielded in a game.
     *
     * Counted through the fielding because a team no longer belongs to a game: what a game holds
     * is the teams that played it, which is a question about fieldings rather than about teams.
     */
    @Query("SELECT COUNT(DISTINCT ts.team.id) FROM TeamSeason ts WHERE ts.game = :game")
    fun countTeamsByGame(@Param("game") game: String): Long

    /**
     * The art this team was last drawn with in this game, so fielding it again carries it across.
     *
     * Newest first by the season's own start rather than by id, because a season added late is
     * still the season it covers.
     */
    @Query(
        """
        SELECT ts FROM TeamSeason ts
        LEFT JOIN FETCH ts.banner
        WHERE ts.team.id = :teamId AND ts.game = :game AND ts.season.id <> :ignoring
        ORDER BY ts.season.startDate DESC
        """,
    )
    fun findPreviousInGame(
        @Param("teamId") teamId: Long,
        @Param("game") game: String,
        @Param("ignoring") ignoring: Long,
    ): List<TeamSeason>

    /**
     * A fielding for this team and season that was dropped, most recently dropped first.
     *
     * Native because `@SQLRestriction` hides a soft-deleted row from every Hibernate query,
     * and this is the one place that has to see one: a team fielded again in a season it was
     * dropped from revives the fielding it had, so the line-up hanging off it comes back with
     * it rather than being stranded behind a second row nothing can reach.
     */
    @Query(
        nativeQuery = true,
        value = """
        SELECT id FROM team_season
        WHERE team_id = :teamId AND game = :game AND season_id = :seasonId
          AND deleted_at <> '9999-12-31 23:59:59.000000'
        ORDER BY deleted_at DESC LIMIT 1
        """,
    )
    fun findDroppedId(
        @Param("teamId") teamId: Long,
        @Param("game") game: String,
        @Param("seasonId") seasonId: Long,
    ): Long?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        nativeQuery = true,
        value = """
        UPDATE team_season SET deleted_at = '9999-12-31 23:59:59.000000', version = version + 1
        WHERE id = :id
        """,
    )
    fun revive(@Param("id") id: Long)
}
