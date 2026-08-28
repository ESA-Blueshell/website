package net.blueshell.api.esports.domain

import net.blueshell.api.esports.persistence.GamePage
import net.blueshell.api.esports.persistence.GamePageRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

/**
 * The games the association knows, and how each presents itself.
 *
 * Every game has a page whether or not a team is still fielded in it, because a retired game
 * keeps its history and somebody may still link to it.
 *
 * A game's code used to be a compiled constant, so a request naming one that did not exist could
 * not be built. It is a row now, so the codes that exist are whatever the rows say, and a request
 * naming something else has to be refused here.
 */
@Service
class GamePageService(private val pages: GamePageRepository) {
    @Transactional(readOnly = true)
    fun findAll(): List<GamePage> = pages.findAllByOrderBySortIndexAsc()

    @Transactional(readOnly = true)
    fun findByGame(game: String): GamePage = require(game)

    /**
     * The game a code names, refused with a reason where none does.
     *
     * Bad request rather than not-found: it is the same answer a code outside the compiled list
     * used to get, when the framework could not turn it into one.
     */
    @Transactional(readOnly = true)
    fun require(game: String): GamePage =
        pages.findByGame(game.trim())
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No game answers to '$game'")

    /** The codes of every game there is, for anything that has to offer a choice of one. */
    @Transactional(readOnly = true)
    fun codes(): List<String> = pages.findAllByOrderBySortIndexAsc().map { it.game }

    /** The game an address belongs to, or nothing where no game answers to it. */
    @Transactional(readOnly = true)
    fun findBySlug(slug: String): GamePage? = pages.findBySlug(slug.trim().lowercase())

    @Transactional
    fun update(game: String, slug: String, intro: String?, sortIndex: Int, fielded: Boolean): GamePage {
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
