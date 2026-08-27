package net.blueshell.api.domain.esports.application

import net.blueshell.api.domain.esports.persistence.GamePage
import net.blueshell.api.domain.esports.persistence.repository.GamePageRepository
import net.blueshell.api.shared.enums.Game
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

/**
 * How each game presents itself.
 *
 * Every game in the enum has a page whether or not the association still fields a team in it,
 * because a retired game keeps its history and somebody may still link to it.
 */
@Service
class GamePageService(private val pages: GamePageRepository) {
    @Transactional(readOnly = true)
    fun findAll(): List<GamePage> = pages.findAllByOrderBySortIndexAsc()

    @Transactional(readOnly = true)
    fun findByGame(game: Game): GamePage =
        pages.findByGame(game) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "$game has no page")

    /** The game an address belongs to, or nothing where no game answers to it. */
    @Transactional(readOnly = true)
    fun findBySlug(slug: String): GamePage? = pages.findBySlug(slug.trim().lowercase())

    @Transactional
    fun update(game: Game, slug: String, intro: String?, sortIndex: Int, fielded: Boolean): GamePage {
        val page = findByGame(game)
        val wanted = slug.trim().lowercase()
        require(wanted.isNotBlank()) { "A game's page needs an address" }
        // An address is how somebody reaches the page; two games cannot share one.
        pages.findBySlug(wanted)?.let { held ->
            if (held.id != page.id) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "${held.game} already answers to '$wanted'")
            }
        }
        page.slug = wanted
        page.intro = intro?.trim()?.ifBlank { null }
        page.sortIndex = sortIndex
        page.fielded = fielded
        return pages.save(page)
    }
}
