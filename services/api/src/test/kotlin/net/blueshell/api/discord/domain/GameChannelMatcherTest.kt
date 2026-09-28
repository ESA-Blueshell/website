package net.blueshell.api.discord.domain

import net.blueshell.api.game.api.GameBlanks
import net.blueshell.api.game.api.GameChannelKind
import net.blueshell.api.game.api.GameService
import net.blueshell.api.game.persistence.Game
import net.blueshell.api.game.persistence.GameChannel
import net.blueshell.api.shared.seed.SeedDatabase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider

class GameChannelMatcherTest {
    private val db = SeedDatabase()
    private val offered = mock<DiscordGameChannels>()
    private val standing = mutableListOf<Game>()

    // The game module as the matcher sees it: the games there are, and their two channel lists.
    private val games = mock<GameService> { on { findAll() } doAnswer { standing.toList() } }
    private val blanks =
        mock<GameBlanks> {
            on { hasChannels(any(), any()) } doAnswer { held(it.getArgument(0), it.getArgument(1)).isNotEmpty() }
            on { addChannels(any(), any(), any()) } doAnswer {
                val given = it.getArgument<List<GameChannel>>(2)
                held(it.getArgument(0), it.getArgument(1)).addAll(given)
                given.size
            }
        }
    private val matcher = GameChannelMatcher(offered, games, blanks, db.dataSource, db.transactions)

    private fun held(
        code: String,
        kind: GameChannelKind,
    ): MutableList<GameChannel> =
        standing.single { it.code == code }.let { if (kind == GameChannelKind.CASUAL) it.channels else it.esportsChannels }

    private fun game(
        code: String,
        name: String,
        slug: String,
    ) {
        standing += Game(code = code, name = name, slug = slug)
    }

    private fun rooms(
        games: List<String>,
        esports: List<String> = emptyList(),
    ) {
        whenever(offered.offered(GameChannelCategory.GAMES)).thenReturn(games.map { TextRoom("id-$it", "324", it, "Games") })
        whenever(offered.offered(GameChannelCategory.ESPORTS)).thenReturn(esports.map { TextRoom("id-$it", "324", it, "Esports") })
    }

    private fun channels(
        kind: GameChannelKind,
        code: String,
    ): List<String> = held(code, kind).map { it.channelName }.sorted()

    @Test
    fun `gives each game the games channel named for it by slug, name or code, and its esports channels`() {
        game("ROCKET_LEAGUE", "Rocket League", "rocketleague")
        game("POKEMON", "Pokémon", "pokemon")
        game("CS2", "Counter-Strike 2", "counter-strike-2")
        game("CSGO", "CS:GO", "counter-strike-global-offensive")
        game("CHESS", "Chess", "chess")
        rooms(
            games = listOf("rocket-league", "pokémon", "counter-strike-2", "general"),
            esports = listOf("counter-strike-2-team", "cs2-scrims", "announcements"),
        )

        assertThat(matcher.apply()).isEqualTo(5)

        assertThat(channels(GameChannelKind.CASUAL, "ROCKET_LEAGUE")).containsExactly("rocket-league")
        assertThat(channels(GameChannelKind.CASUAL, "POKEMON")).containsExactly("pokémon")
        assertThat(channels(GameChannelKind.CASUAL, "CS2")).containsExactly("counter-strike-2")
        assertThat(channels(GameChannelKind.COMPETITION, "CS2")).containsExactly("counter-strike-2-team", "cs2-scrims")
        assertThat(channels(GameChannelKind.CASUAL, "CHESS")).isEmpty()
        assertThat(channels(GameChannelKind.CASUAL, "CSGO")).isEmpty()
    }

    @Test
    fun `matches a game once, and leaves alone a game that has channels or had them taken away`() {
        game("CHESS", "Chess", "chess")
        game("WORDLE", "Wordle", "wordle")
        held("WORDLE", GameChannelKind.CASUAL) += GameChannel("9", "324", "board-games")
        rooms(games = listOf("chess", "wordle"))

        assertThat(matcher.apply()).isEqualTo(1)
        standing.forEach { it.channels.clear() }

        assertThat(matcher.apply()).isZero()
        assertThat(standing.flatMap { it.channels }).isEmpty()
    }

    @Test
    fun `keeps the ledger keys it has always used, so games matched before stay matched`() {
        game("CHESS", "Chess", "chess")
        db.jdbc.update("INSERT INTO seed_applied (seed, record_key) VALUES ('game-channels', 'game_channels|CHESS')")
        rooms(games = listOf("chess"))

        assertThat(matcher.apply()).isZero()
    }

    @Test
    fun `does nothing without a bot, or before the gateway has the server`() {
        game("CHESS", "Chess", "chess")
        whenever(offered.offered(any())).thenReturn(null)
        assertThat(matcher.apply()).isZero()

        rooms(games = emptyList())
        assertThat(matcher.apply()).isZero()
    }

    @Test
    fun `runs when the site is up and whenever the gateway has the server, and a failure never stops either`() {
        var connected: () -> Unit = {}
        val events = mock<MemberEvents> { on { onConnected(any()) } doAnswer { connected = it.getArgument(0) } }
        val provider = mock<ObjectProvider<MemberEvents>> { on { ifAvailable } doReturn events }
        val failing = mock<GameChannelMatcher> { on { apply() } doReturn 2 doThrow IllegalStateException("down") }
        val startup = GameChannelMatcherOnStartup(failing, provider)

        startup.start()
        assertThat(startup.isRunning).isTrue()
        startup.onReady()
        connected()
        startup.stop()

        verify(failing, times(2)).apply()
        assertThat(startup.isRunning).isFalse()
    }

    @Test
    fun `starts without a gateway to listen to`() {
        val provider = mock<ObjectProvider<MemberEvents>> { on { ifAvailable } doReturn null }
        val startup = GameChannelMatcherOnStartup(mock<GameChannelMatcher> { on { apply() } doReturn 0 }, provider)

        startup.start()
        startup.onReady()

        assertThat(startup.isRunning).isTrue()
    }
}
