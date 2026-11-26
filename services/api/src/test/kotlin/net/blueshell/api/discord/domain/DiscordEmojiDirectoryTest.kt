package net.blueshell.api.discord.domain

import net.blueshell.api.discord.domain.DiscordEmojiKind.APPLICATION
import net.blueshell.api.discord.domain.DiscordEmojiKind.SEEN
import net.blueshell.api.discord.domain.DiscordEmojiKind.SERVER
import net.blueshell.api.discord.persistence.SeenEmojiRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
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
import java.time.Instant

private const val SERVER_LIST = "https://discord.test/guilds/324/emojis"
private const val SELF = "https://discord.test/users/@me"
private const val APPLICATION_LIST = "https://discord.test/applications/b1/emojis"

class DiscordEmojiDirectoryTest {
    private val builder = RestClient.builder().baseUrl("https://discord.test")
    private val discord = MockRestServiceServer.bindTo(builder).build()

    private val seen = seenEmojiIndex()

    private fun directory(source: DiscordEmojiSource? = RestDiscordEmojiSource(builder.build(), "324")): DiscordEmojiDirectory {
        val provider: ObjectProvider<DiscordEmojiSource> = mock { on { ifAvailable } doReturn source }
        return DiscordEmojiDirectory(provider, seen)
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
    fun `adds the seen emoji, listing one the server or the application has once, as theirs`() {
        answering()
        val noon = Instant.parse("2026-10-24T12:00:00Z")
        seen.saw(EmojiSighting("9001", "spoopykat", false, "m1", noon))
        seen.saw(EmojiSighting("9001", "spoopykat", false, "m2", noon))
        seen.saw(EmojiSighting("2", "ShellyStar", true, "m1", noon))
        seen.saw(EmojiSighting("42", "shellywave", false, "m1", noon))

        val listed = directory().all()!!
        assertThat(listed.map { listOf(it.id, it.kind, it.uses) }).containsExactly(
            listOf("3", SERVER, null),
            listOf("1", SERVER, null),
            listOf("2", SERVER, null),
            listOf("42", APPLICATION, null),
            listOf("9001", SEEN, 2L),
        )
    }

    @Test
    fun `lists Discord's emoji where the seen emoji cannot be read`() {
        answering()
        val down: SeenEmojiRepository = mock { on { findAllByOrderByUseCountDesc() } doThrow IllegalStateException("down") }
        val source = RestDiscordEmojiSource(builder.build(), "324")
        val provider: ObjectProvider<DiscordEmojiSource> = mock { on { ifAvailable } doReturn source }

        assertThat(DiscordEmojiDirectory(provider, seenEmojiIndex(down)).all()).hasSize(4)
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
        discord.verify()
    }

    @Test
    fun `lists nothing without a bot, or where Discord never answered`() {
        answering(server = false, application = false)

        assertThat(directory(null).all()).isNull()
        assertThat(directory().all()).isNull()
    }

    @Test
    fun `answers the uploaded emoji only once the server's are known, without the seen ones`() {
        seen.saw(EmojiSighting("9001", "spoopykat", false, "m1", Instant.parse("2026-10-24T12:00:00Z")))
        answering(server = false)
        val directory = directory()

        assertThat(directory(null).uploaded()).isNull()
        assertThat(directory.uploaded()).isNull()

        discord.reset()
        answering(application = false)
        assertThat(directory().uploaded()!!.map { it.id }).containsExactlyInAnyOrder("1", "2", "3")
    }
}
