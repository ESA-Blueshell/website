package net.blueshell.api.discord.web

import net.blueshell.api.discord.domain.BotStanding
import net.blueshell.api.discord.domain.BotStandingResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DiscordBotControllerTest {
    @Test
    fun `answers the bot's standing as the standing reads it`() {
        val standing = mock<BotStanding>()
        val read = BotStandingResult(true, true, true, null, emptyList(), emptyList())
        whenever(standing.read()).thenReturn(read)

        assertThat(DiscordBotController(standing, mock(), mock()).findBotStanding()).isSameAs(read)
    }

    @Test
    fun `offers the roles and channels a picker needs, and none without a bot`() {
        val roles: net.blueshell.api.discord.api.DiscordRoleKeeper = mock()
        val channels: net.blueshell.api.discord.api.DiscordChannelKeeper = mock()
        val controller = DiscordBotController(mock(), roles, channels)
        val role =
            net.blueshell.api.discord.api
                .KeptRole("1", "Sitecie", true)
        val channel =
            net.blueshell.api.discord.api
                .KeptChannel("2", "sitecie", net.blueshell.api.discord.api.KeptChannelKind.TEXT, null)
        whenever(roles.roles()).thenReturn(listOf(role))
        whenever(channels.channels()).thenReturn(listOf(channel))

        assertThat(controller.listKeptRoles()).isEmpty()
        assertThat(controller.listKeptChannels()).isEmpty()
        whenever(roles.available()).thenReturn(true)
        whenever(channels.available()).thenReturn(true)
        assertThat(controller.listKeptRoles()).containsExactly(role)
        assertThat(controller.listKeptChannels()).containsExactly(channel)
    }
}
