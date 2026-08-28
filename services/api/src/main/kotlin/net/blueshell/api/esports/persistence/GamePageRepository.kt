package net.blueshell.api.esports.persistence

import net.blueshell.api.shared.enums.Game
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface GamePageRepository : JpaRepository<GamePage, Long> {
    fun findByGame(game: Game): GamePage?

    fun findBySlug(slug: String): GamePage?

    fun findAllByOrderBySortIndexAsc(): List<GamePage>
}
