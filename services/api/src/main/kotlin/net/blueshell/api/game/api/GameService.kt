package net.blueshell.api.game.api

import net.blueshell.api.file.api.StoredPictures
import net.blueshell.api.game.persistence.Game
import net.blueshell.api.game.persistence.GameChannel
import net.blueshell.api.game.persistence.GameRepository
import net.blueshell.api.shared.enums.FileType
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * The games the association knows, and how each presents itself.
 *
 * Every game is answered for whether or not a team is still fielded in it, and whether or not it
 * is archived: a retired game keeps its history and somebody may still link to it. Only a removed
 * game is gone. A code is a row rather than a compiled constant, so a request naming one that
 * does not exist has to be refused here.
 */
@Service
class GameService(
    private val games: GameRepository,
    private val pictures: StoredPictures,
    // Looked up when asked, not injected: the modules implementing these read games through here.
    private val holdings: ObjectProvider<GameHoldings>,
    private val competition: ObjectProvider<GamesInCompetition>,
) {
    @Transactional(readOnly = true)
    fun findAll(): List<Game> = games.findAllByOrderBySortIndexAsc()

    /** The codes of the games in competition now, as the module that fields teams answers. */
    fun inCompetition(): Set<String> = competition.orderedStream().toList().flatMap { it.currentlyFielded() }.toSet()

    @Transactional(readOnly = true)
    fun findByCode(code: String): Game = requireGame(code)

    /** The game a code names, refused as a bad request with a reason where none does. */
    @Transactional(readOnly = true)
    fun requireGame(code: String): Game = games.findByCode(code.trim()) ?: throw UnknownGameCode(code)

    /** The codes of every game there is, for anything that has to offer a choice of one. */
    @Transactional(readOnly = true)
    fun codes(): List<String> = games.findAllByOrderBySortIndexAsc().map { it.code }

    /** The game an address belongs to, or nothing where no game answers to it. */
    @Transactional(readOnly = true)
    fun findBySlug(slug: String): Game? = games.findBySlug(slug.trim().lowercase())

    /**
     * A game the association has started playing. Its code is derived from the name rather than
     * chosen, being the identity everything else points at. Art can wait.
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
        channels: List<GameChannel>? = null,
    ): Game {
        val called = name.trim()
        if (called.isBlank()) throw GameNameBlank()
        val code = codeFor(called)
        if (code.isBlank()) throw GameNameUnusable(called)
        games.findByCode(code)?.let { held -> throw GameAlreadyExists(held.name) }
        val address = addressFor(slug)
        claimed(address, null)
        // A removed game keeps its code, so adding it again brings it back with what was typed.
        games.findRemovedIdByCode(code)?.let { removed ->
            games.restore(removed, address)
            return update(code, called, address, intro, accent, banner, icon, sortIndex, channels ?: emptyList())
        }
        // Unplaced games go at the end; the order is the board's to change after.
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
            ).apply { channels?.let { this.channels.addAll(it.distinctBy(GameChannel::channelId)) } },
        )
    }

    /**
     * A game corrected. Everything is editable except its code, which a team, a roster and a
     * member's handle point at. Whether it is still played is derived from the seasons, not set.
     * A channel may belong to several games, so the same one is fine on another game; on this one
     * it is kept once.
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
        sortIndex: Int?,
        channels: List<GameChannel>? = null,
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
        existing.sortIndex = sortIndex ?: existing.sortIndex
        // Nothing sent keeps the channels it has: the competition pages do not edit them.
        channels?.let {
            existing.channels.clear()
            existing.channels.addAll(it.distinctBy(GameChannel::channelId))
        }
        return games.save(existing)
    }

    /**
     * The codes of the games [codes] name, each once. A game that is archived may stay named where
     * it is among [kept], the ones named before, but it cannot be newly picked.
     */
    @Transactional(readOnly = true)
    fun requireNameable(
        codes: Collection<String>,
        kept: Collection<String> = emptySet(),
    ): List<String> =
        codes.map { requireGame(it) }.distinctBy { it.code }.map { game ->
            if (game.archived && game.code !in kept) throw GameArchived(game.name)
            game.code
        }

    /** A game archived, or brought back to the games played; casual only, competition is untouched. */
    @Transactional
    fun archive(
        game: String,
        archived: Boolean,
    ): Game {
        val existing = requireGame(game)
        existing.archived = archived
        return games.save(existing)
    }

    /**
     * What removing a game would touch, by what each module calls it, with the game's own
     * channels among them. Read so the offer to remove it can say so before it is agreed to.
     */
    @Transactional(readOnly = true)
    fun heldAgainst(game: String): Map<String, Long> {
        val existing = requireGame(game)
        val code = existing.code
        val own = mapOf("channels" to existing.channels.size.toLong())
        return holdings.orderedStream().toList().fold(own) { held, module ->
            held + module.heldAgainst(code).mapValues { (kind, count) -> (held[kind] ?: 0) + count }
        }
    }

    /**
     * A game taken off the site: gone from every page, list and picker, its row kept.
     *
     * Only an archived game can go, so removing is always the second of two steps; [archiveFirst]
     * takes both at once, for the competition pages, whose own confirmation asks for both. Every
     * module holding something against it is asked first, and any may refuse: a game that
     * carries history stays, archived or not, and everything it played stays readable.
     */
    @Transactional
    fun remove(
        game: String,
        archiveFirst: Boolean = false,
    ) {
        val existing = requireGame(game)
        if (archiveFirst) existing.archived = true
        if (!existing.archived) throw GameNotArchived(existing.name)
        holdings.orderedStream().forEach { it.refuseRemoval(existing.code) }
        games.remove(existing.id!!)
    }

    /**
     * A code from a name: what everything else points at, so it carries no punctuation and no
     * case. "Rocket League" is ROCKET_LEAGUE, the way the games already recorded read.
     */
    private fun codeFor(name: String): String =
        name
            .uppercase()
            .map { if (it.isLetterOrDigit()) it else '_' }
            .joinToString("")
            .trim('_')
            .replace(Regex("_+"), "_")
            .take(CODE_LENGTH)

    /** An address somebody can be sent to: no case, no spaces, nothing that reads as a path. */
    private fun addressFor(slug: String): String {
        val address =
            slug
                .trim()
                .lowercase()
                .map { if (it.isLetterOrDigit()) it else '-' }
                .joinToString("")
                .trim('-')
                .replace(Regex("-+"), "-")
                .take(SLUG_LENGTH)
        if (address.isBlank()) throw GameAddressBlank()
        if (address in RESERVED) throw AddressReserved(address)
        return address
    }

    /** An address is how somebody reaches a game; two games cannot share one. */
    private fun claimed(
        address: String,
        mine: Long?,
    ) {
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
