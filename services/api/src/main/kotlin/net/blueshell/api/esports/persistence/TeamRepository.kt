package net.blueshell.api.esports.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface TeamRepository : BaseRepository<Team, Long> {
    /**
     * A game's teams, the banners fetched with them.
     *
     * The fetch is load-bearing rather than tidy. A team's response carries the banner's own
     * size and the widths it is stored at, and reading those initialises the association — so
     * without this the list runs two queries per team. It would not fail either: lazy loading
     * outside a transaction is enabled, so the only symptom is the queries.
     */
    @Query(
        "SELECT t FROM Team t LEFT JOIN FETCH t.banner b LEFT JOIN FETCH b._renditions " +
            "WHERE t.game = :game ORDER BY t.name ASC",
    )
    fun findAllByGameOrderByNameAsc(@Param("game") game: String): List<Team>

    fun findByGameAndNameIgnoreCase(game: String, name: String): Team?

    fun countByGame(game: String): Long
}
