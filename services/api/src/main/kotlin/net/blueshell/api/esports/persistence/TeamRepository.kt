package net.blueshell.api.esports.persistence

import net.blueshell.api.shared.enums.Game
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface TeamRepository : BaseRepository<Team, Long> {
    fun findAllByGameOrderByNameAsc(game: Game): List<Team>

    fun findByGameAndNameIgnoreCase(game: Game, name: String): Team?
}
