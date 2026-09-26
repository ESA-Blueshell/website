package net.blueshell.api.discord.domain

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
    private val matcher = GameChannelMatcher(offered, db.dataSource, db.transactions)

    private fun game(
        code: String,
        name: String,
        slug: String,
    ) = db.jdbc.update("INSERT INTO game (code, name, slug, sort_index) VALUES (?, ?, ?, 1)", code, name, slug)

    private fun rooms(
        games: List<String>,
        esports: List<String> = emptyList(),
    ) {
        whenever(offered.offered(GameChannelCategory.GAMES)).thenReturn(games.map { TextRoom("id-$it", "324", it, "Games") })
        whenever(offered.offered(GameChannelCategory.ESPORTS)).thenReturn(esports.map { TextRoom("id-$it", "324", it, "Esports") })
    }

    private fun channels(
        table: String,
        code: String,
    ): List<String?> =
        db.jdbc.queryForList(
            "SELECT c.channel_name FROM $table c JOIN game g ON g.id = c.game_id WHERE g.code = ? ORDER BY 1",
            String::class.java,
            code,
        )

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

        assertThat(channels("game_channels", "ROCKET_LEAGUE")).containsExactly("rocket-league")
        assertThat(channels("game_channels", "POKEMON")).containsExactly("pokémon")
        assertThat(channels("game_channels", "CS2")).containsExactly("counter-strike-2")
        assertThat(channels("game_esports_channels", "CS2")).containsExactly("counter-strike-2-team", "cs2-scrims")
        assertThat(channels("game_channels", "CHESS")).isEmpty()
        assertThat(channels("game_channels", "CSGO")).isEmpty()
    }

    @Test
    fun `matches a game once, and leaves alone a game that has channels or had them taken away`() {
        game("CHESS", "Chess", "chess")
        game("WORDLE", "Wordle", "wordle")
        db.jdbc.update(
            "INSERT INTO game_channels (game_id, channel_id, guild_id, channel_name) " +
                "SELECT id, '9', '324', 'board-games' FROM game WHERE code = 'WORDLE'",
        )
        rooms(games = listOf("chess", "wordle"))

        assertThat(matcher.apply()).isEqualTo(1)
        db.jdbc.update("DELETE FROM game_channels")

        assertThat(matcher.apply()).isZero()
        assertThat(db.count("SELECT COUNT(*) FROM game_channels")).isZero()
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
