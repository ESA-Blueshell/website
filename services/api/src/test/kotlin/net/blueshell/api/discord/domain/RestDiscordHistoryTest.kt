package net.blueshell.api.discord.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient

class RestDiscordHistoryTest {
    private val builder = RestClient.builder().baseUrl("https://discord.test")
    private val discord = MockRestServiceServer.bindTo(builder).build()
    private val history = RestDiscordHistory(builder.build(), "324")

    private fun answers(
        url: String,
        json: String,
    ) {
        discord.expect(requestTo(url)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON))
    }

    @Test
    fun `finds every channel, thread and forum post holding messages`() {
        // A text channel, a voice channel with its chat, a forum and an announcement channel the bot may not list threads of.
        answers(
            "https://discord.test/guilds/324/channels",
            """[{"id": "10", "type": 0}, {"id": "11", "type": 2}, {"id": "12", "type": 15}, {"id": "13", "type": 5}]""",
        )
        answers("https://discord.test/guilds/324/threads/active", """{"threads": [{"id": "20"}]}""")
        answers(
            "https://discord.test/channels/10/threads/archived/public?limit=100",
            // Discord writes the time with an offset, whose plus would read as a space if sent as it is.
            """
            {"threads": [{"id": "21", "thread_metadata": {"archive_timestamp": "2026-01-02T00:00:00.123456+00:00"}}], "has_more": true}
            """.trimIndent(),
        )
        answers(
            "https://discord.test/channels/10/threads/archived/public?limit=100&before=2026-01-02T00:00:00.123456Z",
            """{"threads": [{"id": "22", "thread_metadata": {"archive_timestamp": "2026-01-01T00:00:00Z"}}], "has_more": false}""",
        )
        answers("https://discord.test/channels/12/threads/archived/public?limit=100", """{"threads": [], "has_more": true}""")
        discord
            .expect(requestTo("https://discord.test/channels/13/threads/archived/public?limit=100"))
            .andRespond(withStatus(HttpStatus.FORBIDDEN))

        assertThat(history.places()).containsExactly("10", "11", "13", "20", "21", "22")
        discord.verify()
    }

    @Test
    fun `reads message text only where the application has Message Content, limited or not`() {
        answers("https://discord.test/applications/@me", """{"id": "b1", "flags": ${1 shl 18}}""")
        answers("https://discord.test/applications/@me", """{"id": "b1", "flags": ${1 shl 19}}""")
        answers("https://discord.test/applications/@me", """{"id": "b1", "flags": ${1 shl 12}}""")

        assertThat(listOf(history.readsText(), history.readsText(), history.readsText())).containsExactly(true, true, false)
        discord.verify()
    }

    @Test
    fun `reads a page of messages older than a message, and nothing where the bot may not read`() {
        val newest =
            (101..200).map {
                """{"id": "$it", "timestamp": "2026-10-24T12:00:00Z", "author": {"id": "u1"}, "content": "<:kat:7>"}"""
            }
        answers("https://discord.test/channels/10/messages?limit=100", newest.reversed().joinToString(prefix = "[", postfix = "]"))
        answers("https://discord.test/users/@me", """{"id": "b1"}""")
        answers("https://discord.test/channels/10/messages?limit=100&before=101", """[{"id": "1"}]""")
        answers("https://discord.test/channels/10/messages?limit=100&before=1", "[]")
        discord.expect(requestTo("https://discord.test/channels/11/messages?limit=100")).andRespond(withStatus(HttpStatus.NOT_FOUND))
        discord.expect(requestTo("https://discord.test/channels/12/messages?limit=100")).andRespond(withServerError())

        val first = history.page("10", null)!!
        assertThat(listOf(first.sightings.size, first.oldestMessageId, first.last, first.messages)).containsExactly(100, "101", false, 100)
        assertThat(history.page("10", "101")).isEqualTo(HistoryPage(emptyList(), "1", true, messages = 1))
        assertThat(history.page("10", "1")).isEqualTo(HistoryPage(emptyList(), "1", true))
        assertThat(history.page("11", null)).isNull()
        assertThatThrownBy { history.page("12", null) }.isInstanceOf(HttpServerErrorException::class.java)
        discord.verify()
    }
}
