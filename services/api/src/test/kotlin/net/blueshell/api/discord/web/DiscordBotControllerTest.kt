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

        assertThat(DiscordBotController(standing, mock(), mock(), mock(), mock()).findBotStanding()).isSameAs(read)
    }

    @Test
    fun `offers the roles and channels a picker needs, and none without a bot`() {
        val roles: net.blueshell.api.discord.api.DiscordRoleKeeper = mock()
        val channels: net.blueshell.api.discord.api.DiscordChannelKeeper = mock()
        val controller = DiscordBotController(mock(), mock(), roles, channels, mock())
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

    @Test
    fun `makes a game channel and reads and sets a channel's access through the policies`() {
        val policies: net.blueshell.api.discord.domain.GameChannelPolicies = mock()
        val controller = DiscordBotController(mock(), policies, mock(), mock(), mock())
        val made =
            net.blueshell.api.discord.domain
                .MadeChannel("1", "99", "bs-valo")
        val access = net.blueshell.api.discord.domain.AccessPolicy.DEFAULT
        val state =
            net.blueshell.api.discord.domain
                .ChannelAccessState(access, access)
        whenever(policies.create("bs-valo", net.blueshell.api.discord.domain.GameChannelCategory.GAMES)).thenReturn(made)
        whenever(policies.read("1")).thenReturn(state)
        whenever(policies.set("1", access)).thenReturn(state)

        assertThat(controller.createGameChannel(CreateGameChannelRequest("bs-valo"))).isSameAs(made)
        assertThat(controller.findChannelAccess("1")).isSameAs(state)
        assertThat(controller.setChannelAccess("1", access)).isSameAs(state)
    }

    @Test
    fun `lists the catalogued channels for the Discord page`() {
        val catalogue: net.blueshell.api.discord.domain.DiscordCatalogue = mock()
        val listed =
            listOf(
                net.blueshell.api.discord.domain.CataloguedChannel(
                    "1",
                    "sitecie",
                    net.blueshell.api.discord.api.KeptChannelKind.TEXT,
                    "Committees",
                    true,
                    listOf("900"),
                    null,
                    null,
                ),
            )
        whenever(catalogue.channels()).thenReturn(listed)

        assertThat(DiscordBotController(mock(), mock(), mock(), mock(), catalogue).listCataloguedChannels()).isSameAs(listed)
    }
}
