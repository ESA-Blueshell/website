package net.blueshell.api.discord.domain

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.concrete.Category
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.springframework.beans.factory.ObjectProvider

class DiscordChannelDirectoryTest {
    // Which channels @everyone may view, as JDA works it out from the base permissions and overrides.
    private val viewable = mutableSetOf<String>()
    private val everyone: Role =
        mock {
            on {
                hasPermission(any<GuildChannel>(), eq(Permission.VIEW_CHANNEL))
            } doAnswer { call ->
                call.getArgument<GuildChannel>(0).id in viewable
            }
        }

    private fun category(
        id: String,
        name: String,
    ): Category =
        mock {
            on { this.id } doReturn id
            on { this.name } doReturn name
        }

    private fun channel(
        id: String,
        name: String,
        parent: Category? = null,
        seen: Boolean = true,
    ): TextChannel {
        if (seen) viewable += id
        return mock {
            on { this.id } doReturn id
            on { this.name } doReturn name
            on { parentCategory } doReturn parent
        }
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
    fun `lists the channels everybody can view, in the server's order, categories left out`() {
        val board = category("10", "Board")
        val server =
            guild(
                channel("1", "general"),
                channel("2", "events-info"),
                channel("5", "secret", seen = false),
                board,
                channel("3", "board-talk", parent = board, seen = false),
                channel("4", "open-in-board", parent = board),
            )

        assertThat(directory { server }.open()!!.map { it.name }).containsExactly("general", "events-info", "open-in-board")
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
