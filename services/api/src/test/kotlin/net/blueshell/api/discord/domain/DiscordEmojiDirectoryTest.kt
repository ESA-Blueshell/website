package net.blueshell.api.discord.domain

import net.blueshell.clients.discord.api.DiscordApi
import net.blueshell.clients.discord.model.EmojiResponse
import net.blueshell.clients.discord.model.GuildWithCountsResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.ObjectProvider

class DiscordEmojiDirectoryTest {
    private fun emoji(
        id: String,
        name: String,
        animated: Boolean = false,
    ): EmojiResponse =
        mock {
            on { this.id } doReturn id
            on { this.name } doReturn name
            on { this.animated } doReturn animated
        }

    private fun guild(vararg emoji: EmojiResponse): GuildWithCountsResponse = mock { on { emojis } doReturn emoji.toList() }

    private fun directory(api: DiscordApi?): DiscordEmojiDirectory {
        val provider: ObjectProvider<DiscordApi> = mock { on { ifAvailable } doReturn api }
        return DiscordEmojiDirectory(provider, "324")
    }

    @Test
    fun `lists every emoji the server has, by name`() {
        // Built before the stubbing that returns it: a mock made inside another's stubbing leaves it unfinished.
        val server = guild(emoji("2", "ShellyStar", animated = true), emoji("1", "POGGERS"))
        val api: DiscordApi = mock { on { getGuild("324", false) } doReturn server }

        assertThat(directory(api).all())
            .containsExactly(DiscordEmoji("1", "POGGERS", false), DiscordEmoji("2", "ShellyStar", true))
    }

    @Test
    fun `keeps the list rather than asking Discord on every read`() {
        val server = guild(emoji("1", "POGGERS"))
        val api: DiscordApi = mock { on { getGuild("324", false) } doReturn server }
        val directory = directory(api)

        directory.all()
        assertThat(directory.all()).containsExactly(DiscordEmoji("1", "POGGERS", false))
        verify(api, times(1)).getGuild("324", false)
    }

    @Test
    fun `lists nothing without a bot, or where Discord never answered`() {
        val failing: DiscordApi = mock { on { getGuild("324", false) } doThrow IllegalStateException("down") }

        assertThat(directory(null).all()).isNull()
        assertThat(directory(failing).all()).isNull()
    }
}
