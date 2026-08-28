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
 * Reads and writes the game records.
 *
 * Every game has a page whether or not a team is still fielded in it: a retired game keeps its
 * history and existing links to it must keep working.
 *
 * A game's code used to be a compiled enum constant, so an unknown code could not be written in
 * Kotlin at all. Codes are now whatever rows exist, so unknown ones are rejected here instead.
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
     * The game with this code, or a 400 naming the code that matched nothing.
     *
     * 400 rather than 404, because that is what Spring already returned when it could not convert
     * an unknown value into a Game enum constant.
     */
    @Transactional(readOnly = true)
    fun requireGame(game: String): GamePage =
        pages.findByGame(game.trim())
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "There is no game with the code '$game'.")

    /** Every existing game code, for anything that has to offer a choice of one. */
    @Transactional(readOnly = true)
    fun codes(): List<String> = pages.findAllByOrderBySortIndexAsc().map { it.game }

    /** The game served from this page address, or null if none is. */
    @Transactional(readOnly = true)
    fun findBySlug(slug: String): GamePage? = pages.findBySlug(slug.trim().lowercase())

    /**
     * Creates a game from a name and a page address.
     *
     * The code is derived from the name rather than supplied, because it is what teams and game
     * accounts reference and two people naming the same game must not produce two rows. Images are
     * optional: a game without them renders on the site's brand colour.
     */
    @Transactional
    fun create(name: String, slug: String): GamePage {
        val called = name.trim()
        if (called.isBlank()) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "A game needs a name.")
        val code = codeFor(called)
        if (code.isBlank()) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "A game's name needs at least one letter or number.",
            )
        }
        pages.findByGame(code)?.let { held ->
            throw ResponseStatusException(HttpStatus.CONFLICT, "There is already a game called ${held.name}.")
        }
        val address = addressFor(slug)
        claimed(address, null)
        val last = pages.findAllByOrderBySortIndexAsc().lastOrNull()?.sortIndex ?: 0
        return pages.save(GamePage(game = code, name = called, slug = address, sortIndex = last + 1))
    }

    /**
     * Updates a game. Everything is editable except the code, which teams, rosters and game
     * accounts reference.
     *
     * Setting `fielded` to false archives it: it stops appearing in menus and when adding a team,
     * and keeps every team, season and roster entry it has.
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
        if (called.isBlank()) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "A game needs a name.")
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
     * How many teams reference this game, and how many roster entries those teams hold.
     *
     * Read before the delete confirmation is shown, so the counts are visible when deciding.
     */
    @Transactional(readOnly = true)
    fun contentsOf(game: String): Pair<Long, Long> {
        val code = requireGame(game).game
        return teams.countByGame(code) to entries.countByGame(code)
    }

    /**
     * Deletes a game that has no teams.
     *
     * A game with teams is rejected: archiving it with `fielded = false` is the operation that
     * fits, and it keeps the history. What remains is a game with nothing to preserve, so the row
     * is deleted outright rather than soft-deleted — the code is unique across every row, and a
     * soft-deleted row would hold it permanently.
     */
    @Transactional
    fun delete(game: String) {
        val page = requireGame(game)
        val (held, players) = contentsOf(page.game)
        if (held > 0) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "${page.name} has $held team${if (held == 1L) "" else "s"} with " +
                    "$players ${if (players == 1L) "person" else "people"} listed, so it cannot be " +
                    "deleted. Uncheck \"Active\" instead to archive it: its page and history stay online.",
            )
        }
        pages.delete(page)
    }

    /**
     * Derives a code from a name: uppercase, with every run of other characters replaced by a
     * single underscore. "Rocket League" becomes ROCKET_LEAGUE, matching the existing codes.
     */
    private fun codeFor(name: String): String =
        name.uppercase().map { if (it.isLetterOrDigit()) it else '_' }
            .joinToString("").trim('_').replace(Regex("_+"), "_").take(CODE_LENGTH)

    /** Normalises a page address: lowercase, non-alphanumeric runs replaced by a single hyphen. */
    private fun addressFor(slug: String): String {
        val address = slug.trim().lowercase().map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("").trim('-').replace(Regex("-+"), "-").take(SLUG_LENGTH)
        if (address.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "A game needs a page address.")
        }
        if (address in RESERVED) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "'$address' is reserved for the esports index page. Pick another address.",
            )
        }
        return address
    }

    /** Two games cannot share a page address, since it is what the route resolves on. */
    private fun claimed(address: String, mine: Long?) {
        pages.findBySlug(address)?.let { held ->
            if (held.id != mine) {
                throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The address '$address' is already used by ${held.name}.",
                )
            }
        }
    }

    private companion object {
        const val CODE_LENGTH = 32
        const val SLUG_LENGTH = 64

        /** Paths under /esports that are not games. A game using one would be unreachable. */
        val RESERVED = setOf("competitive-scene")
    }
}
