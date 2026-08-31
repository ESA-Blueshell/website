package net.blueshell.api.esports.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface GameRepository : JpaRepository<Game, Long> {
    fun findByCode(code: String): Game?

    fun findBySlug(slug: String): Game?

    fun findAllByOrderBySortIndexAsc(): List<Game>
}
