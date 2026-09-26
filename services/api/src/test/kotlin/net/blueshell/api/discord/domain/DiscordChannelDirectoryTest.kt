package net.blueshell.api.discord.domain

import net.blueshell.clients.discord.api.DiscordApi
import net.blueshell.clients.discord.model.ChannelPermissionOverwriteResponse
import net.blueshell.clients.discord.model.ChannelTypes
import net.blueshell.clients.discord.model.ListGuildChannels200ResponseInner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DiscordChannelDirectoryTest {
    private fun rule(
        deny: Long,
        on: String = "324",
    ): ChannelPermissionOverwriteResponse =
        mock {
            on { id } doReturn on
            on { this.deny } doReturn deny.toString()
        }

    private fun channel(
        id: String,
        name: String,
        position: Int,
        type: ChannelTypes = ChannelTypes._0,
        parent: String? = null,
        rules: List<ChannelPermissionOverwriteResponse>? = null,
    ): ListGuildChannels200ResponseInner =
        mock {
            on { this.id } doReturn id
            on { this.name } doReturn name
            on { this.position } doReturn position
            on { this.type } doReturn type
            on { parentId } doReturn parent
            on { permissionOverwrites } doReturn rules
        }

    private fun directory(api: DiscordApi?): DiscordChannelDirectory {
        val provider: ObjectProvider<DiscordApi> = mock { on { ifAvailable } doReturn api }
        return DiscordChannelDirectory(provider, "324")
    }

    private val hidden = DiscordChannelDirectory.VIEW_CHANNEL

    @Test
    fun `lists the channels everybody can see, in the server's order, categories left out`() {
        // Built before the stubbing that returns them: a mock made inside another's stubbing leaves it unfinished.
        val channels =
            listOf(
                channel("2", "events-info", 2),
                channel("1", "general", 1),
                channel("10", "Board", 0, type = ChannelTypes._4, rules = listOf(rule(hidden))),
                channel("3", "board-talk", 3, parent = "10"),
                channel("4", "open-in-board", 4, parent = "10", rules = listOf(rule(0))),
                channel("5", "secret", 5, rules = listOf(rule(0, on = "901"), rule(hidden))),
                channel("6", "orphan", 6, parent = "77"),
            )
        val api: DiscordApi = mock { on { listGuildChannels("324") } doReturn channels }

        assertThat(directory(api).open()!!.map { it.name }).containsExactly("general", "events-info", "open-in-board", "orphan")
    }

    @Test
    fun `keeps them five minutes, and the last list where Discord stops answering`() {
        val channels = listOf(channel("1", "general", 1))
        val api: DiscordApi = mock { on { listGuildChannels("324") } doReturn channels }
        val directory = directory(api)
        val start = Instant.parse("2026-09-26T10:00:00Z")
        directory.clock = Clock.fixed(start, ZoneOffset.UTC)

        directory.open()
        directory.open()
        verify(api, times(1)).listGuildChannels("324")

        directory.clock = Clock.fixed(start.plus(DiscordChannelDirectory.KEPT_FOR), ZoneOffset.UTC)
        whenever(api.listGuildChannels("324")).thenThrow(IllegalStateException("down"))
        assertThat(directory.open()).containsExactly(DiscordChannel("1", "general"))
    }

    @Test
    fun `lists nothing without a bot, or where Discord never answered`() {
        val failing: DiscordApi = mock { on { listGuildChannels("324") } doThrow IllegalStateException("down") }

        assertThat(directory(null).open()).isNull()
        assertThat(directory(failing).open()).isNull()
    }
}
