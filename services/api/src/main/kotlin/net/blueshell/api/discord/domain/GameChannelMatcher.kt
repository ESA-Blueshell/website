package net.blueshell.api.discord.domain

import net.blueshell.api.game.api.GameBlanks
import net.blueshell.api.game.api.GameChannelKind
import net.blueshell.api.game.api.GameService
import net.blueshell.api.game.persistence.GameChannel
import net.blueshell.api.shared.seed.SeedLedger
import net.blueshell.api.shared.seed.SeedOrder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.SmartLifecycle
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.jdbc.datasource.DataSourceUtils
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.text.Normalizer
import javax.sql.DataSource

/**
 * Gives each game the server's channel named for it: a channel under the games category whose name
 * is the game's slug, name or code, and under the esports category one whose name holds it. Each
 * game is given one once (see [SeedLedger]), and never where it has channels already, so the
 * board's later choices outlive every start.
 *
 * Runs when the site is up, since that is when the games stand, and whenever the gateway has the
 * server, since until then it has no channels to offer.
 */
@Component
class GameChannelMatcher(
    private val channels: DiscordGameChannels,
    private val games: GameService,
    private val blanks: GameBlanks,
    private val dataSource: DataSource,
    private val transactions: TransactionTemplate,
) {
    /** How many channels a run gave out, which is none without a bot or once every game has one. */
    @Synchronized
    fun apply(): Int {
        val casualRooms = channels.offered(GameChannelCategory.GAMES).orEmpty()
        val esportsRooms = channels.offered(GameChannelCategory.ESPORTS).orEmpty()
        if (casualRooms.isEmpty() && esportsRooms.isEmpty()) return 0
        return transactions.execute {
            // The ledger writes through the transaction's own connection, beside the game module's.
            val connection = DataSourceUtils.getConnection(dataSource)
            try {
                match(SeedLedger(connection, SEED), casualRooms, esportsRooms)
            } finally {
                DataSourceUtils.releaseConnection(connection, dataSource)
            }
        }
    }

    private fun match(
        ledger: SeedLedger,
        casualRooms: List<TextRoom>,
        esportsRooms: List<TextRoom>,
    ): Int {
        val standing = games.findAll().map { game -> StandingGame(game.code, keysOf(game.code, game.name, game.slug)) }
        val casual = casualRooms.associateWith { room -> standing.firstOrNull { plain(room.name) in it.keys } }
        val competition = esportsRooms.associateWith { room -> heldBy(room, standing) }
        return standing.sumOf { game ->
            give(ledger, game, GameChannelKind.CASUAL, casual.filterValues { it == game }.keys) +
                give(ledger, game, GameChannelKind.COMPETITION, competition.filterValues { it == game }.keys)
        }
    }

    /* The longest name wins, so a channel for one game is not taken by a game whose name is inside it. */
    private fun heldBy(
        room: TextRoom,
        standing: List<StandingGame>,
    ): StandingGame? =
        standing
            .flatMap { game -> game.keys.filter { it in plain(room.name) }.map { it to game } }
            .maxByOrNull { (key) -> key.length }
            ?.second

    private fun give(
        ledger: SeedLedger,
        game: StandingGame,
        kind: GameChannelKind,
        rooms: Collection<TextRoom>,
    ): Int {
        val key = "${LEDGER_TABLE.getValue(kind)}|${game.code}"
        if (rooms.isEmpty() || !ledger.toWrite(key) { blanks.hasChannels(game.code, kind) }) return 0
        return blanks.addChannels(game.code, kind, rooms.map { GameChannel(it.id, it.guildId, it.name) })
    }

    private data class StandingGame(
        val code: String,
        val keys: Set<String>,
    )

    private companion object {
        const val SEED = "game-channels"

        /** The ledger keys each kind by the table it once wrote, so games matched before stay matched. */
        val LEDGER_TABLE = mapOf(GameChannelKind.CASUAL to "game_channels", GameChannelKind.COMPETITION to "game_esports_channels")

        /** Shorter than this, a key is found inside too many channel names that are not the game's. */
        const val MIN_KEY = 3

        fun keysOf(vararg names: String?): Set<String> = names.mapNotNull { it?.let(::plain) }.filter { it.length >= MIN_KEY }.toSet()

        /** Pokémon, pokemon and poke-mon are one name: accents, case and punctuation dropped. */
        fun plain(name: String): String =
            Normalizer
                .normalize(name, Normalizer.Form.NFD)
                .replace(Regex("\\p{M}"), "")
                .lowercase()
                .filter { it in 'a'..'z' || it in '0'..'9' }
    }
}

/** A separate bean so the transaction is opened by the proxy, and a failure never blocks start-up. */
@Component
class GameChannelMatcherOnStartup(
    private val matcher: GameChannelMatcher,
    private val events: ObjectProvider<MemberEvents>,
) : SmartLifecycle {
    @Volatile private var running = false

    @Order(SeedOrder.LINKS)
    @EventListener(ApplicationReadyEvent::class)
    fun onReady() = run()

    override fun start() {
        running = true
        events.ifAvailable?.onConnected(::run)
    }

    override fun stop() {
        running = false
    }

    override fun isRunning(): Boolean = running

    private fun run() {
        try {
            val written = matcher.apply()
            if (written > 0) log.info("[game-channels] {} channels given to games by name", written)
        } catch (e: Exception) {
            log.warn("[game-channels] could not match the server's channels to games: {}", e.message)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(GameChannelMatcherOnStartup::class.java)
    }
}
