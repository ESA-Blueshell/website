package net.blueshell.api.discord.domain

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
import java.sql.Connection
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
    private val dataSource: DataSource,
    private val transactions: TransactionTemplate,
) {
    /** How many channels a run gave out, which is none without a bot or once every game has one. */
    @Synchronized
    fun apply(): Int {
        val games = channels.offered(GameChannelCategory.GAMES).orEmpty()
        val esports = channels.offered(GameChannelCategory.ESPORTS).orEmpty()
        if (games.isEmpty() && esports.isEmpty()) return 0
        return transactions.execute {
            val connection = DataSourceUtils.getConnection(dataSource)
            try {
                match(connection, games, esports)
            } finally {
                DataSourceUtils.releaseConnection(connection, dataSource)
            }
        } ?: 0
    }

    private fun match(
        connection: Connection,
        games: List<TextRoom>,
        esports: List<TextRoom>,
    ): Int {
        val ledger = SeedLedger(connection, SEED)
        val standing = standingGames(connection)
        val casual = games.associateWith { room -> standing.firstOrNull { plain(room.name) in it.keys } }
        val competition = esports.associateWith { room -> heldBy(room, standing) }
        return standing.sumOf { game ->
            give(connection, ledger, game, "game_channels", casual.filterValues { it == game }.keys) +
                give(connection, ledger, game, "game_esports_channels", competition.filterValues { it == game }.keys)
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
        connection: Connection,
        ledger: SeedLedger,
        game: StandingGame,
        table: String,
        rooms: Collection<TextRoom>,
    ): Int {
        if (rooms.isEmpty() || !ledger.toWrite("$table|${game.code}") { holds(connection, table, game.id) }) return 0
        return rooms.sumOf { room ->
            connection.prepareStatement("INSERT INTO $table (game_id, channel_id, guild_id, channel_name) VALUES (?, ?, ?, ?)").use {
                it.setLong(1, game.id)
                listOf(room.id, room.guildId, room.name).forEachIndexed { at, value -> it.setString(at + 2, value) }
                it.executeUpdate()
            }
        }
    }

    private fun holds(
        connection: Connection,
        table: String,
        gameId: Long,
    ): Boolean =
        connection.prepareStatement("SELECT 1 FROM $table WHERE game_id = ?").use { statement ->
            statement.setLong(1, gameId)
            statement.executeQuery().use { rows -> rows.next() }
        }

    private fun standingGames(connection: Connection): List<StandingGame> =
        connection.prepareStatement("SELECT id, code, name, slug FROM game WHERE $ACTIVE ORDER BY sort_index, id").use { statement ->
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        val code = rows.getString(2)
                        val keys = listOf(code, rows.getString(3), rows.getString(4)).mapNotNull { it?.let(::plain) }
                        add(StandingGame(rows.getLong(1), code, keys.filter { it.length >= MIN_KEY }.toSet()))
                    }
                }
            }
        }

    private data class StandingGame(
        val id: Long,
        val code: String,
        val keys: Set<String>,
    )

    private companion object {
        /** The sentinel a live row carries, as every soft-deleted table here uses it. */
        const val ACTIVE = "deleted_at = '9999-12-31 23:59:59'"
        const val SEED = "game-channels"

        /** Shorter than this, a key is found inside too many channel names that are not the game's. */
        const val MIN_KEY = 3

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
