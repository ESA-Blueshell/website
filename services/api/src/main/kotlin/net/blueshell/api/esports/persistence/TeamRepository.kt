package net.blueshell.api.esports.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface TeamRepository : BaseRepository<Team, Long> {
    /**
     * Every team the association has, the logos fetched with them.
     *
     * Not asked per game: the pool is shared, so a team that has only played League is still one
     * the board can field in Valorant. The fetch is load-bearing — a team's response reads its
     * logo's size and widths, so without it the list runs two queries per team, silently, since
     * lazy loading outside a transaction is enabled.
     */
    @Query("SELECT t FROM Team t LEFT JOIN FETCH t.icon i LEFT JOIN FETCH i._renditions ORDER BY t.name ASC")
    fun findAllOrderByNameAsc(): List<Team>

    fun findByNameIgnoreCase(name: String): Team?
}
