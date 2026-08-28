package net.blueshell.api.esports.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface TeamRepository : BaseRepository<Team, Long> {
    fun findAllByGameOrderByNameAsc(game: String): List<Team>

    fun findByGameAndNameIgnoreCase(game: String, name: String): Team?
}
