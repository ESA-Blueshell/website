package net.blueshell.api.discord.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount.once
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class DiscordEmojiDirectoryTest {
    private val builder = RestClient.builder().baseUrl("https://discord.test")
    private val discord = MockRestServiceServer.bindTo(builder).build()

    private fun directory(source: ServerEmojiSource? = RestServerEmojiSource(builder.build(), "324")): DiscordEmojiDirectory {
        val provider: ObjectProvider<ServerEmojiSource> = mock { on { ifAvailable } doReturn source }
        return DiscordEmojiDirectory(provider)
    }

    // Discord's list of the server's emoji as it answers it; names and IDs are made up.
    private val listed =
        """
        [
          {"id": "2", "name": "ShellyStar", "roles": [], "require_colons": true, "managed": false, "animated": true, "available": true},
          {"id": "1", "name": "POGGERS", "roles": ["90", "91"], "require_colons": true, "managed": false, "animated": false, "available": true},
          {"id": "3", "name": "BS_PeepoHappy", "roles": [], "require_colons": true, "managed": false, "animated": false, "available": false}
        ]
        """.trimIndent()

    @Test
    fun `lists every emoji the server has by name, read off the server's emoji list`() {
        discord
            .expect(once(), requestTo("https://discord.test/guilds/324/emojis"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(listed, MediaType.APPLICATION_JSON))

        assertThat(directory().all()).containsExactly(
            DiscordEmoji("3", "BS_PeepoHappy", animated = false, available = false, roleIds = emptyList()),
            DiscordEmoji("1", "POGGERS", animated = false, available = true, roleIds = listOf("90", "91")),
            DiscordEmoji("2", "ShellyStar", animated = true, available = true, roleIds = emptyList()),
        )
        discord.verify()
    }

    @Test
    fun `keeps the list rather than asking Discord on every read`() {
        discord
            .expect(once(), requestTo("https://discord.test/guilds/324/emojis"))
            .andRespond(withSuccess(listed, MediaType.APPLICATION_JSON))
        val directory = directory()

        directory.all()
        assertThat(directory.all()).hasSize(3)
        discord.verify()
    }

    @Test
    fun `lists nothing without a bot, or where Discord never answered`() {
        discord.expect(requestTo("https://discord.test/guilds/324/emojis")).andRespond(withServerError())

        assertThat(directory(null).all()).isNull()
        assertThat(directory().all()).isNull()
    }
}
