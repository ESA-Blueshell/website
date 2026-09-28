package net.blueshell.api.game.api

import net.blueshell.api.game.persistence.Game
import net.blueshell.api.game.persistence.GameChannel
import net.blueshell.api.game.persistence.GameRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class GameBlanksTest {
    private val games = mock<GameRepository>()
    private val blanks = GameBlanks(games)
    private val chess = Game(code = "CHESS", name = "Chess", slug = "chess")

    @Test
    fun `offers a game channels of one kind, adding only the ones it has not got`() {
        chess.channels += GameChannel("1", "324", "chess")
        whenever(games.findByCode("CHESS")).thenReturn(chess)

        assertThat(blanks.hasChannels("CHESS", GameChannelKind.CASUAL)).isTrue()
        assertThat(blanks.hasChannels("CHESS", GameChannelKind.COMPETITION)).isFalse()

        val team = GameChannel("2", "324", "chess-team")
        assertThat(blanks.addChannels("CHESS", GameChannelKind.COMPETITION, listOf(team, team))).isEqualTo(1)
        assertThat(blanks.addChannels("CHESS", GameChannelKind.CASUAL, listOf(GameChannel("1", "324", "chess")))).isZero()
        assertThat(chess.esportsChannels.map { it.channelId }).containsExactly("2")
        assertThat(chess.channels.map { it.channelId }).containsExactly("1")
    }

    @Test
    fun `refuses to say anything about the channels of a game nobody holds`() {
        assertThatThrownBy { blanks.hasChannels("PONG", GameChannelKind.CASUAL) }.isInstanceOf(UnknownGameCode::class.java)
    }
}
