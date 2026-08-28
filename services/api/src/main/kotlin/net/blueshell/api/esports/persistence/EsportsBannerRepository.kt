package net.blueshell.api.esports.persistence

import net.blueshell.api.shared.enums.Game
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface EsportsBannerRepository : JpaRepository<EsportsBanner, Long> {
    /**
     * Every banner set for one game, the file fetched with it.
     *
     * The whole set rather than a query per level: a game holds a handful of these, and which
     * one wins is decided by comparing them against each other.
     */
    @Query(
        """
        SELECT b FROM EsportsBanner b
        JOIN FETCH b.file
        LEFT JOIN FETCH b.season
        LEFT JOIN FETCH b.team
        WHERE b.game = :game
        """,
    )
    fun findAllByGame(@Param("game") game: Game): List<EsportsBanner>
}
