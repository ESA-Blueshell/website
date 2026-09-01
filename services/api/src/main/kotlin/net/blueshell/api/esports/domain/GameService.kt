package net.blueshell.api.esports.domain

import net.blueshell.api.esports.persistence.Game
import net.blueshell.api.esports.persistence.GameRepository
import net.blueshell.api.esports.persistence.TeamSeasonRepository
import net.blueshell.api.esports.persistence.TeamRosterEntryRepository
import net.blueshell.api.file.api.StoredPictures
import net.blueshell.api.shared.enums.FileType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * The games the association knows, and how each presents itself.
 *
 * Every game is answered for whether or not a team is still fielded in it, because a retired
 * game keeps its history and somebody may still link to it.
 *
 * A game's code used to be a compiled constant, so a request naming one that did not exist could
 * not be built. It is a row now, so the codes that exist are whatever the rows say, and a request
 * naming something else has to be refused here.
 */
@Service
class GameService(
    private val games: GameRepository,
    private val fielded: TeamSeasonRepository,
    private val entries: TeamRosterEntryRepository,
    private val pictures: StoredPictures,
) {
    @Transactional(readOnly = true)
    fun findAll(): List<Game> = games.findAllByOrderBySortIndexAsc()

    @Transactional(readOnly = true)
    fun findByCode(code: String): Game = requireGame(code)

    /**
     * The game a code names, refused with a reason where none does.
     *
     * Bad request rather than not-found: it is the same answer a code outside the compiled list
     * used to get, when the framework could not turn it into one.
     */
    @Transactional(readOnly = true)
    fun requireGame(code: String): Game =
        games.findByCode(code.trim()) ?: throw UnknownGameCode(code)

    /** The codes of every game there is, for anything that has to offer a choice of one. */
    @Transactional(readOnly = true)
    fun codes(): List<String> = games.findAllByOrderBySortIndexAsc().map { it.code }

    /** The game an address belongs to, or nothing where no game answers to it. */
    @Transactional(readOnly = true)
    fun findBySlug(slug: String): Game? = games.findBySlug(slug.trim().lowercase())

    /**
     * A game the association has started playing.
     *
     * The caller says what it is called and what address it answers to; its code is taken from the
     * name, because a code is the identity everything else points at and is nobody's to choose
     * twice. Art can wait: a game with none reads on the island's own colour.
     */
    @Transactional
    @Suppress("LongParameterList")
    fun create(
        name: String,
        slug: String,
        intro: String? = null,
        accent: String? = null,
        banner: String? = null,
        icon: String? = null,
        sortIndex: Int? = null,
    ): Game {
        val called = name.trim()
        if (called.isBlank()) throw GameNameBlank()
        val code = codeFor(called)
        if (code.isBlank()) throw GameNameUnusable(called)
        games.findByCode(code)?.let { held -> throw GameAlreadyExists(held.name) }
        val address = addressFor(slug)
        claimed(address, null)
        // Where nobody says where it goes, it goes at the end. A game added mid-season is the
        // newest thing the association plays, and the order is the board\'s to change after.
        val last = games.findAllByOrderBySortIndexAsc().lastOrNull()?.sortIndex ?: 0
        return games.save(
            Game(
                code = code,
                name = called,
                slug = address,
                intro = intro?.trim()?.ifBlank { null },
                accent = accent?.trim()?.ifBlank { null },
                banner = pictures.of(banner, FileType.GAME_BANNER),
                icon = pictures.of(icon, FileType.GAME_ICON),
                sortIndex = sortIndex ?: (last + 1),
            ),
        )
    }

    /**
     * A game corrected. Everything about it is editable except its code, which is the identity
     * a team, a roster and a member's handle already point at.
     *
     * Whether the association still plays it is not among them: that is derived from the
     * seasons now, and a game stops being current by not being entered rather than by being
     * marked.
     */
    @Transactional
    @Suppress("LongParameterList")
    fun update(
        game: String,
        name: String,
        slug: String,
        intro: String?,
        accent: String?,
        banner: String?,
        icon: String?,
        sortIndex: Int,
    ): Game {
        val existing = findByCode(game)
        val called = name.trim()
        if (called.isBlank()) throw GameNameBlank()
        val wanted = addressFor(slug)
        claimed(wanted, existing.id)
        existing.name = called
        existing.slug = wanted
        existing.intro = intro?.trim()?.ifBlank { null }
        existing.accent = accent?.trim()?.ifBlank { null }
        // The pictures were stored when they were chosen; the save is what puts them on the game.
        existing.banner = pictures.of(banner, FileType.GAME_BANNER)
        existing.icon = pictures.of(icon, FileType.GAME_ICON)
        existing.sortIndex = sortIndex
        return games.save(existing)
    }

    /**
     * What a game holds: teams recorded in it, and the people on their line-ups.
     *
     * Read so a removal can say what it would take before it is agreed to, rather than after.
     */
    @Transactional(readOnly = true)
    fun contentsOf(game: String): Pair<Long, Long> {
        val code = requireGame(game).code
        return fielded.countTeamsByGame(code) to entries.countByGame(code)
    }

    /**
     * A game added by mistake, taken off the site.
     *
     * A game that carries history cannot go: it is refused, and everything it played stays
     * readable. There is no softer act to offer any more — a game leaves the front of the site
     * by not being entered in a season, which is a thing that happens rather than a thing
     * somebody does. What is left is a game holding nothing, which has no history to keep, so
     * it is removed rather than hidden. Its code is unique across every row, and a hidden row
     * would hold that code for good.
     */
    @Transactional
    fun delete(game: String) {
        val existing = requireGame(game)
        val (held, players) = contentsOf(existing.code)
        if (held > 0) throw GameHoldsHistory(existing.name, held, players)
        games.delete(existing)
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
        if (address.isBlank()) throw GameAddressBlank()
        if (address in RESERVED) throw AddressReserved(address)
        return address
    }

    /** An address is how somebody reaches a game; two games cannot share one. */
    private fun claimed(address: String, mine: Long?) {
        games.findBySlug(address)?.let { held ->
            if (held.id != mine) throw AddressTaken(held.name, address)
        }
    }

    private companion object {
        const val CODE_LENGTH = 32
        const val SLUG_LENGTH = 64

        /** Addresses under /esports that are not a game's, so a game claiming one is unreachable. */
        val RESERVED = setOf("competitive-scene")
    }
}
