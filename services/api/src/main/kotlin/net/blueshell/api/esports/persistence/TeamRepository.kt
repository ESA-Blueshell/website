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
     * The pool is shared across games, so this is not asked per game: a team that has only ever
     * played League of Legends is still a team the board can field in Valorant, and a picker
     * that hid it would make the shared pool invisible.
     *
     * The fetch is load-bearing rather than tidy. A team's response carries its logo's own size
     * and the widths it is stored at, and reading those initialises the association — so without
     * this the list runs two queries per team. It would not fail either: lazy loading outside a
     * transaction is enabled, so the only symptom is the queries.
     */
    @Query("SELECT t FROM Team t LEFT JOIN FETCH t.icon i LEFT JOIN FETCH i._renditions ORDER BY t.name ASC")
    fun findAllOrderByNameAsc(): List<Team>

    fun findByNameIgnoreCase(name: String): Team?
}
