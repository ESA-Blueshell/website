package net.blueshell.api.discord.domain

import net.blueshell.api.discord.domain.DiscordEmojiKind.APPLICATION
import net.blueshell.api.discord.domain.DiscordEmojiKind.SERVER
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

private const val SERVER_LIST = "https://discord.test/guilds/324/emojis"
private const val SELF = "https://discord.test/users/@me"
private const val APPLICATION_LIST = "https://discord.test/applications/b1/emojis"

class DiscordEmojiDirectoryTest {
    private val builder = RestClient.builder().baseUrl("https://discord.test")
    private val discord = MockRestServiceServer.bindTo(builder).build()

    private fun directory(source: DiscordEmojiSource? = RestDiscordEmojiSource(builder.build(), "324")): DiscordEmojiDirectory {
        val provider: ObjectProvider<DiscordEmojiSource> = mock { on { ifAvailable } doReturn source }
        return DiscordEmojiDirectory(provider)
    }

    // Discord's lists of the server's and the application's emoji as it answers them; names and IDs are made up.
    private val serverListed =
        """
        [
          {"id": "2", "name": "ShellyStar", "roles": [], "require_colons": true, "managed": false, "animated": true, "available": true},
          {"id": "1", "name": "POGGERS", "roles": ["90", "91"], "require_colons": true, "managed": false, "animated": false, "available": true},
          {"id": "3", "name": "BS_PeepoHappy", "roles": [], "require_colons": true, "managed": false, "animated": false, "available": false}
        ]
        """.trimIndent()
    private val applicationListed = """{"items": [{"id": "42", "name": "shellywave", "roles": [], "animated": false, "available": true}]}"""

    private fun answering(
        server: Boolean = true,
        application: Boolean = true,
    ) {
        discord
            .expect(once(), requestTo(SERVER_LIST))
            .andExpect(method(HttpMethod.GET))
            .andRespond(if (server) withSuccess(serverListed, MediaType.APPLICATION_JSON) else withServerError())
        discord.expect(once(), requestTo(SELF)).andRespond(withSuccess("""{"id": "b1"}""", MediaType.APPLICATION_JSON))
        discord
            .expect(once(), requestTo(APPLICATION_LIST))
            .andRespond(if (application) withSuccess(applicationListed, MediaType.APPLICATION_JSON) else withServerError())
    }

    @Test
    fun `lists the server's emoji and the bot's application emoji by name, each with its kind`() {
        answering()

        assertThat(directory().all()).containsExactly(
            DiscordEmoji("3", "BS_PeepoHappy", animated = false, available = false, roleIds = emptyList(), kind = SERVER),
            DiscordEmoji("1", "POGGERS", animated = false, available = true, roleIds = listOf("90", "91"), kind = SERVER),
            DiscordEmoji("2", "ShellyStar", animated = true, available = true, roleIds = emptyList(), kind = SERVER),
            DiscordEmoji("42", "shellywave", animated = false, available = true, roleIds = emptyList(), kind = APPLICATION),
        )
        discord.verify()
    }

    @Test
    fun `keeps both lists rather than asking Discord on every read`() {
        answering()
        val directory = directory()

        directory.all()
        assertThat(directory.all()).hasSize(4)
        discord.verify()
    }

    @Test
    fun `lists what Discord answered where one list could not be read`() {
        answering(server = false)

        assertThat(directory().all()!!.map { it.name }).containsExactly("shellywave")
    }

    @Test
    fun `lists nothing without a bot, or where Discord never answered`() {
        answering(server = false, application = false)

        assertThat(directory(null).all()).isNull()
        assertThat(directory().all()).isNull()
    }
}
