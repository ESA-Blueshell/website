package net.blueshell.api.discord.domain

import net.blueshell.api.discord.persistence.ChannelAccess
import net.blueshell.api.game.api.GameService
import net.blueshell.api.game.persistence.Game
import net.blueshell.api.game.persistence.GameChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class GameAccessTest {
    private val games: GameService = mock()
    private val policies: GameChannelPolicies = mock()
    private val access = GameAccess(games, policies)
    private val readOnly = AccessPolicy(ChannelAccess.READ, ChannelAccess.READ)
    private val valorant =
        Game(code = "VALO", name = "Valorant", slug = "valorant").apply {
            channels.add(GameChannel("1", "99", "bs-valo"))
            channels.add(GameChannel("3", "99", "gone"))
            esportsChannels.add(GameChannel("2", "99", "blueshell-valorant"))
            esportsChannels.add(GameChannel("1", "99", "bs-valo"))
        }

    @Test
    fun `reads every channel of the game, its shared policy, and leaves out a channel gone from Discord`() {
        whenever(games.findByCode("VALO")).thenReturn(valorant)
        whenever(policies.read("1")).thenReturn(ChannelAccessState(null, AccessPolicy.DEFAULT))
        whenever(policies.read("2")).thenReturn(ChannelAccessState(readOnly, AccessPolicy.DEFAULT))
        whenever(policies.read("3")).thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND))

        val read = access.read("VALO")

        assertThat(read.policy).isEqualTo(readOnly)
        assertThat(read.channels.map { it.id to it.name }).containsExactly("1" to "bs-valo", "2" to "blueshell-valorant")
        assertThat(read.channels[1].state.differs).isTrue()
    }

    @Test
    fun `reads the default where no channel keeps a policy, and sets one policy on every channel`() {
        whenever(games.findByCode("VALO")).thenReturn(valorant)
        whenever(policies.read("1")).thenReturn(ChannelAccessState(null, AccessPolicy.DEFAULT))
        whenever(policies.read("2")).thenReturn(ChannelAccessState(null, AccessPolicy.DEFAULT))
        whenever(policies.read("3")).thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND))

        assertThat(access.read("VALO").policy).isEqualTo(AccessPolicy.DEFAULT)
        access.set("VALO", readOnly)

        verify(policies).set("1", readOnly)
        verify(policies).set("2", readOnly)
        verify(policies, never()).set("3", readOnly)

        org.mockito.kotlin
            .doThrow(ResponseStatusException(HttpStatus.BAD_GATEWAY))
            .whenever(policies)
            .read("3")
        org.assertj.core.api.Assertions
            .assertThatThrownBy { access.read("VALO") }
            .isInstanceOf(ResponseStatusException::class.java)
    }
}
