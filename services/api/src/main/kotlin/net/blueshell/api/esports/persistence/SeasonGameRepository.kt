package net.blueshell.api.esports.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface SeasonGameRepository : JpaRepository<SeasonGame, Long> {
    fun findBySeasonIdAndGame(seasonId: Long, game: String): SeasonGame?

    /** The games entered in a season, in the order their records put them. */
    @Query("SELECT sg.game FROM SeasonGame sg WHERE sg.season.id = :seasonId")
    fun gamesIn(@Param("seasonId") seasonId: Long): List<String>

    /**
     * A game entered in this season and then taken out, most recently taken out first.
     *
     * Native because `@SQLRestriction` hides a soft-deleted row from every Hibernate query,
     * and this is the one place that has to see one: entering a game again revives the row it
     * had rather than writing a second, which the unique index would otherwise allow.
     */
    @Query(
        nativeQuery = true,
        value = """
        SELECT id FROM season_game
        WHERE season_id = :seasonId AND game = :game
          AND deleted_at <> '9999-12-31 23:59:59.000000'
        ORDER BY deleted_at DESC LIMIT 1
        """,
    )
    fun findDroppedId(@Param("seasonId") seasonId: Long, @Param("game") game: String): Long?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        nativeQuery = true,
        value = """
        UPDATE season_game SET deleted_at = '9999-12-31 23:59:59.000000', version = version + 1
        WHERE id = :id
        """,
    )
    fun revive(@Param("id") id: Long)
}
