package net.blueshell.api.discord.domain

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.PermissionOverride
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.concrete.Category
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.beans.factory.ObjectProvider
import java.util.EnumSet

class DiscordChannelDirectoryTest {
    private val everyone: Role = mock()

    private fun rule(vararg denied: Permission): PermissionOverride {
        val set = EnumSet.noneOf(Permission::class.java).apply { addAll(denied) }
        return mock { on { this.denied } doReturn set }
    }

    private fun category(
        id: String,
        name: String,
        rule: PermissionOverride? = null,
    ): Category =
        mock {
            on { this.id } doReturn id
            on { this.name } doReturn name
            on { getPermissionOverride(everyone) } doReturn rule
        }

    private fun channel(
        id: String,
        name: String,
        parent: Category? = null,
        rule: PermissionOverride? = null,
    ): TextChannel =
        mock {
            on { this.id } doReturn id
            on { this.name } doReturn name
            on { parentCategory } doReturn parent
            on { getPermissionOverride(everyone) } doReturn rule
        }

    // Channels are built before the guild's stubbing: a mock made inside another's stubbing leaves it unfinished.
    private fun guild(vararg channels: GuildChannel): Guild =
        mock {
            on { publicRole } doReturn everyone
            on { this.channels } doReturn channels.toList()
        }

    private fun directory(gateway: GatewayGuild?): DiscordChannelDirectory {
        val provider: ObjectProvider<GatewayGuild> = mock { on { ifAvailable } doReturn gateway }
        return DiscordChannelDirectory(provider)
    }

    @Test
    fun `lists the channels everybody can see, in the server's order, categories left out`() {
        val board = category("10", "Board", rule(Permission.VIEW_CHANNEL))
        val server =
            guild(
                channel("1", "general"),
                channel("2", "events-info"),
                channel("5", "secret", rule = rule(Permission.VIEW_CHANNEL)),
                channel("6", "orphan"),
                board,
                channel("3", "board-talk", parent = board),
                channel("4", "open-in-board", parent = board, rule = rule(Permission.MESSAGE_SEND)),
            )

        assertThat(directory { server }.open()!!.map { it.name }).containsExactly("general", "events-info", "orphan", "open-in-board")
    }

    @Test
    fun `names each channel's category, and none for a channel outside one`() {
        val games = category("20", "Games")
        val server = guild(channel("1", "general"), games, channel("7", "valorant", parent = games))

        assertThat(directory { server }.open())
            .containsExactly(DiscordChannel("1", "general", null), DiscordChannel("7", "valorant", "Games"))
    }

    @Test
    fun `keeps the last channels while the gateway is away`() {
        val server = guild(channel("1", "general"))
        var held: Guild? = server
        val directory = directory { held }

        assertThat(directory.open()).containsExactly(DiscordChannel("1", "general", null))
        held = null
        assertThat(directory.open()).containsExactly(DiscordChannel("1", "general", null))
    }

    @Test
    fun `lists nothing without a bot, or while the gateway has never had the server`() {
        assertThat(directory(null).open()).isNull()
        assertThat(directory { null }.open()).isNull()
    }
}
