package net.blueshell.api.esports.domain

import net.blueshell.api.esports.persistence.GamePage
import net.blueshell.api.esports.persistence.GamePageRepository
import net.blueshell.api.esports.persistence.TeamRepository
import net.blueshell.api.esports.persistence.TeamRosterEntryRepository
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
class GamePageService(
    private val pages: GamePageRepository,
    private val teams: TeamRepository,
    private val entries: TeamRosterEntryRepository,
) {
    @Transactional(readOnly = true)
    fun findAll(): List<GamePage> = pages.findAllByOrderBySortIndexAsc()

    @Transactional(readOnly = true)
    fun findByGame(game: String): GamePage = requireGame(game)

    /**
     * The game a code names, refused with a reason where none does.
     *
     * Bad request rather than not-found: it is the same answer a code outside the compiled list
     * used to get, when the framework could not turn it into one.
     */
    @Transactional(readOnly = true)
    fun requireGame(game: String): GamePage =
        pages.findByGame(game.trim())
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No game answers to '$game'")

    /** The codes of every game there is, for anything that has to offer a choice of one. */
    @Transactional(readOnly = true)
    fun codes(): List<String> = pages.findAllByOrderBySortIndexAsc().map { it.game }

    /** The game an address belongs to, or nothing where no game answers to it. */
    @Transactional(readOnly = true)
    fun findBySlug(slug: String): GamePage? = pages.findBySlug(slug.trim().lowercase())

    /**
     * A game the association has started playing.
     *
     * The caller says what it is called and what its page answers to; its code is taken from the
     * name, because a code is the identity everything else points at and is nobody's to choose
     * twice. Art can wait: a game with none reads on the island's own colour.
     */
    @Transactional
    fun create(name: String, slug: String): GamePage {
        val called = name.trim()
        if (called.isBlank()) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "A game needs a name")
        val code = codeFor(called)
        if (code.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "'$called' has no letters or digits to name it by")
        }
        pages.findByGame(code)?.let { held ->
            throw ResponseStatusException(HttpStatus.CONFLICT, "${held.name} is already a game")
        }
        val address = addressFor(slug)
        claimed(address, null)
        val last = pages.findAllByOrderBySortIndexAsc().lastOrNull()?.sortIndex ?: 0
        return pages.save(GamePage(game = code, name = called, slug = address, sortIndex = last + 1))
    }

    /**
     * A game corrected. Everything about it is editable except its code, which is the identity
     * a team, a roster and a member's handle already point at.
     *
     * Marking it no longer fielded is the soft act: it stops being offered as current and keeps
     * every team, season and roster place it holds.
     */
    @Transactional
    @Suppress("LongParameterList")
    fun update(
        game: String,
        name: String,
        slug: String,
        intro: String?,
        accent: String?,
        mark: String?,
        banner: String?,
        sortIndex: Int,
        fielded: Boolean,
    ): GamePage {
        val page = findByGame(game)
        val called = name.trim()
        if (called.isBlank()) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "A game needs a name")
        val wanted = addressFor(slug)
        claimed(wanted, page.id)
        page.name = called
        page.slug = wanted
        page.intro = intro?.trim()?.ifBlank { null }
        page.accent = accent?.trim()?.ifBlank { null }
        page.mark = mark?.trim()?.ifBlank { null }
        page.banner = banner?.trim()?.ifBlank { null }
        page.sortIndex = sortIndex
        page.fielded = fielded
        return pages.save(page)
    }

    /**
     * What a game holds: teams recorded in it, and the roster places those carry.
     *
     * Read so a removal can say what it would take before it is agreed to, rather than after.
     */
    @Transactional(readOnly = true)
    fun contentsOf(game: String): Pair<Long, Long> {
        val code = requireGame(game).game
        return teams.countByGame(code) to entries.countByGame(code)
    }

    /**
     * A game added by mistake, taken off the site.
     *
     * A game that carries history cannot go: it is refused, and marking it no longer fielded is
     * the act that fits — everything it played stays readable. What is left is a game holding
     * nothing, which has no history to keep, so it is removed rather than hidden. Its code is
     * unique across every row, and a hidden row would hold that code for good.
     */
    @Transactional
    fun delete(game: String) {
        val page = requireGame(game)
        val (held, players) = contentsOf(page.game)
        if (held > 0) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "${page.name} holds $held team${if (held == 1L) "" else "s"} and " +
                    "$players roster place${if (players == 1L) "" else "s"}. " +
                    "Mark it as no longer fielded instead, and everything it played stays readable.",
            )
        }
        pages.delete(page)
    }

    /**
     * A code from a name: what everything else points at, so it carries no punctuation and no
     * case. "Rocket League" is ROCKET_LEAGUE, the way the games already recorded read.
     */
    private fun codeFor(name: String): String =
        name.uppercase().map { if (it.isLetterOrDigit()) it else '_' }
            .joinToString("").trim('_').replace(Regex("_+"), "_").take(CODE_LENGTH)

    /** An address somebody can be sent to: no case, no spaces, nothing that reads as a path. */
    private fun addressFor(slug: String): String {
        val address = slug.trim().lowercase().map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("").trim('-').replace(Regex("-+"), "-").take(SLUG_LENGTH)
        if (address.isBlank()) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "A game's page needs an address")
        if (address in RESERVED) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "'$address' is the esports index's own address")
        }
        return address
    }

    /** An address is how somebody reaches a page; two games cannot share one. */
    private fun claimed(address: String, mine: Long?) {
        pages.findBySlug(address)?.let { held ->
            if (held.id != mine) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "${held.name} already answers to '$address'")
            }
        }
    }

    private companion object {
        const val CODE_LENGTH = 32
        const val SLUG_LENGTH = 64

        /** Addresses under /esports that are not a game's, so a game claiming one is unreachable. */
        val RESERVED = setOf("competitive-scene")
    }
}
