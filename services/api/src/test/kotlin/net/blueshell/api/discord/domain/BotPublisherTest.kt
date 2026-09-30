package net.blueshell.api.discord.domain

import net.blueshell.api.shared.job.ExplainedJobFailure
import net.blueshell.api.sync.api.DiscordEventListing
import net.blueshell.api.sync.api.DiscordImage
import net.blueshell.api.sync.api.DiscordLink
import net.blueshell.api.sync.api.DiscordPost
import net.blueshell.clients.discord.api.DiscordApi
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import java.time.Instant

class BotPublisherTest {
    private val api: DiscordApi = mock()
    private val doors: DoorSource =
        object : DoorSource {
            override fun textRooms() = listOf(TextRoom("111", "324", "📣events-info"))

            override fun invite(channelId: String): String? = null
        }
    private val mapper = JsonMapper.builder().build()
    private val builder = RestClient.builder().baseUrl("https://discord.test")
    private val discord = MockRestServiceServer.bindTo(builder).build()
    private val publisher = BotPublisher(api, builder.build(), mapper, doors, "324")

    private val banner = DiscordImage("banner.webp", "image/webp", byteArrayOf(1, 2, 3))

    private val post =
        DiscordPost(
            pingedRoleIds = listOf("901", "902"),
            text = "## LAN party\n\nBring a rig.\n\n<@&901> <@&902>",
        )

    private val listing =
        DiscordEventListing(
            name = "LAN party",
            description = "Bring a rig.",
            location = "Pakhuis",
            start = Instant.parse("2026-10-10T18:00:00Z"),
            end = Instant.parse("2026-10-10T21:00:00Z"),
            cover = "data:x",
        )

    @Test
    fun `links a message and a Discord event where Discord opens them`() {
        assertThat(publisher.linkOf("events-info", "m1")).isEqualTo("https://discord.com/channels/324/111/m1")
        assertThat(publisher.linkOf("events-lobby", "222/m1")).isEqualTo("https://discord.com/channels/324/222/m1")
        assertThat(publisher.linkOf("events-lobby", "m1")).isNull()
        assertThat(publisher.linkOfDiscordEvent("e1")).isEqualTo("https://discord.com/events/324/e1")
    }

    @Test
    fun `posts plain text in the channel of that name, notifying only the pinged roles`() {
        discord
            .expect(requestTo("https://discord.test/channels/111/messages"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.CONTENT_TYPE, containsString("application/json")))
            .andExpect(jsonPath("$.flags").value(1 shl 15))
            .andExpect(jsonPath("$.content").value(""))
            .andExpect(jsonPath("$.embeds").isEmpty())
            .andExpect(jsonPath("$.attachments").isEmpty())
            .andExpect(jsonPath("$.components.length()").value(1))
            .andExpect(jsonPath("$.components[0].type").value(10))
            .andExpect(jsonPath("$.components[0].content").value(post.text))
            .andExpect(jsonPath("$.allowed_mentions.parse").isEmpty())
            .andExpect(jsonPath("$.allowed_mentions.roles", containsInAnyOrder("901", "902")))
            .andRespond(withSuccess("""{"id": "m1"}""", MediaType.APPLICATION_JSON))

        assertThat(publisher.post("events-info", post)).isEqualTo("111/m1")

        discord.verify()
        verifyNoInteractions(api)
    }

    @Test
    fun `edits a post, one made as an embed included, without notifying anybody`() {
        discord
            .expect(requestTo("https://discord.test/channels/111/messages/m1"))
            .andExpect(method(HttpMethod.PATCH))
            .andExpect(jsonPath("$.flags").value(1 shl 15))
            .andExpect(jsonPath("$.content").value(""))
            .andExpect(jsonPath("$.embeds").isEmpty())
            .andExpect(jsonPath("$.attachments").isEmpty())
            .andExpect(jsonPath("$.components[0].content").value(post.text))
            .andExpect(jsonPath("$.allowed_mentions.parse").isEmpty())
            .andExpect(jsonPath("$.allowed_mentions.roles").isEmpty())
            .andRespond(withSuccess("""{"id": "m1"}""", MediaType.APPLICATION_JSON))

        assertThat(publisher.edit("events-info", "m1", post)).isTrue()

        discord.verify()
        verifyNoInteractions(api)
    }

    @Test
    fun `shows the banner above the text, on a post and on an edit`() {
        discord
            .expect(requestTo("https://discord.test/channels/111/messages"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.CONTENT_TYPE, containsString("multipart/form-data")))
            .andExpect(content().string(containsString("filename=\"banner.webp\"")))
            .andExpect(content().string(containsString("""{"type":12,"items":[{"media":{"url":"attachment://banner.webp"}}]}""")))
            .andExpect(content().string(containsString(""""attachments":[{"id":"0","filename":"banner.webp"}]""")))
            .andExpect(content().string(containsString("<@&901> <@&902>")))
            .andRespond(withSuccess("""{"id": "m1"}""", MediaType.APPLICATION_JSON))
        discord
            .expect(requestTo("https://discord.test/channels/111/messages/m1"))
            .andExpect(method(HttpMethod.PATCH))
            .andExpect(content().string(containsString("filename=\"banner.webp\"")))
            .andExpect(content().string(containsString("attachment://banner.webp")))
            .andRespond(withSuccess("""{"id": "m1"}""", MediaType.APPLICATION_JSON))

        assertThat(publisher.post("events-info", post.copy(banner = banner))).isEqualTo("111/m1")
        publisher.edit("events-info", "m1", post.copy(banner = banner))

        discord.verify()
        verifyNoInteractions(api)
    }

    @Test
    fun `puts the links under the message as buttons, with a banner and without`() {
        val links =
            listOf(
                DiscordLink("More on the site", "https://site/events/42"),
                DiscordLink("Sign up", "https://site/events/42#signup"),
            )
        val linked = post.copy(links = links)
        discord
            .expect(requestTo("https://discord.test/channels/111/messages"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.CONTENT_TYPE, containsString("application/json")))
            .andExpect(jsonPath("$.components[1].type").value(1))
            .andExpect(jsonPath("$.components[1].components[0].style").value(5))
            .andExpect(jsonPath("$.components[1].components[1].label").value("Sign up"))
            .andExpect(jsonPath("$.components[1].components[1].url").value("https://site/events/42#signup"))
            .andRespond(withSuccess("""{"id": "m1"}""", MediaType.APPLICATION_JSON))
        discord
            .expect(requestTo("https://discord.test/channels/111/messages/m1"))
            .andExpect(method(HttpMethod.PATCH))
            .andExpect(jsonPath("$.components[1].components[0].label").value("More on the site"))
            .andRespond(withSuccess("""{"id": "m1"}""", MediaType.APPLICATION_JSON))
        discord
            .expect(requestTo("https://discord.test/channels/111/messages"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string(containsString("filename=\"banner.webp\"")))
            .andExpect(content().string(containsString("\"style\":5")))
            .andRespond(withSuccess("""{"id": "m2"}""", MediaType.APPLICATION_JSON))

        assertThat(publisher.post("events-info", linked)).isEqualTo("111/m1")
        publisher.edit("events-info", "m1", linked)
        assertThat(publisher.post("events-info", linked.copy(banner = banner))).isEqualTo("111/m2")

        discord.verify()
        verifyNoInteractions(api)
    }

    @Test
    fun `says the gateway is not connected while it has no channels, and names a channel it cannot find`() {
        val down =
            object : DoorSource {
                override fun textRooms() = emptyList<TextRoom>()

                override fun invite(channelId: String): String? = null
            }

        assertThatThrownBy { BotPublisher(api, builder.build(), mapper, down, "324").post("events-info", post) }
            .isInstanceOf(ExplainedJobFailure::class.java)
            .hasMessage("The Discord gateway is not connected, so no channel called #events-info can be found yet.")
        assertThatThrownBy { publisher.post("events-calendar", post) }
            .hasMessage("The Discord server has no text channel called #events-calendar.")
        verifyNoInteractions(api)
    }

    @Test
    fun `posts and edits without the banner where Discord refuses the file, and gives up on anything else`() {
        discord.expect(requestTo("https://discord.test/channels/111/messages")).andRespond(withStatus(HttpStatus.CONTENT_TOO_LARGE))
        discord
            .expect(requestTo("https://discord.test/channels/111/messages"))
            .andExpect(jsonPath("$.attachments").isEmpty())
            .andExpect(jsonPath("$.components[0].type").value(10))
            .andRespond(withSuccess("""{"id": "m1"}""", MediaType.APPLICATION_JSON))
        discord.expect(requestTo("https://discord.test/channels/111/messages/m1")).andRespond(withStatus(HttpStatus.CONTENT_TOO_LARGE))
        discord
            .expect(requestTo("https://discord.test/channels/111/messages/m1"))
            .andExpect(jsonPath("$.attachments").isEmpty())
            .andRespond(withSuccess("""{"id": "m1"}""", MediaType.APPLICATION_JSON))
        discord.expect(requestTo("https://discord.test/channels/111/messages")).andRespond(withServerError())
        discord.expect(requestTo("https://discord.test/channels/111/messages")).andRespond(withBadRequest())

        assertThat(publisher.post("events-info", post.copy(banner = banner))).isEqualTo("111/m1")
        publisher.edit("events-info", "m1", post.copy(banner = banner))
        assertThatThrownBy { publisher.post("events-info", post.copy(banner = banner)) }
            .isInstanceOf(ExplainedJobFailure::class.java)
            .hasCauseInstanceOf(HttpServerErrorException::class.java)
        assertThatThrownBy { publisher.post("events-info", post) }.isInstanceOf(HttpClientErrorException::class.java)
        discord.verify()
    }

    @Test
    fun `counts a message or Discord event already gone as removed, and passes on anything else`() {
        whenever(api.deleteMessage("111", "m1")).thenThrow(HttpClientErrorException(HttpStatus.NOT_FOUND))
        publisher.delete("events-info", "m1")

        whenever(api.deleteGuildScheduledEvent("324", "e1")).thenThrow(HttpClientErrorException(HttpStatus.CONFLICT))
        assertThatThrownBy { publisher.deleteDiscordEvent("e1") }.isInstanceOf(HttpClientErrorException::class.java)
    }

    @Test
    fun `reaches a message by the channel its reference carries, whatever the channel is called now`() {
        discord
            .expect(requestTo("https://discord.test/channels/222/messages/m1"))
            .andExpect(method(HttpMethod.PATCH))
            .andRespond(withSuccess("""{"id": "m1"}""", MediaType.APPLICATION_JSON))

        publisher.edit("events-lobby", "222/m1", post)
        publisher.delete("events-lobby", "222/m2")

        discord.verify()
        verify(api).deleteMessage("222", "m2")
    }

    @Test
    fun `asks Discord after a message and a Discord event by their IDs, answering a message's reference as it stands`() {
        discord.expect(requestTo("https://discord.test/channels/222/messages/m1")).andRespond(withSuccess())
        discord.expect(requestTo("https://discord.test/channels/111/messages/m2")).andRespond(withSuccess())
        discord.expect(requestTo("https://discord.test/channels/111/messages/m3")).andRespond(withStatus(HttpStatus.NOT_FOUND))
        discord.expect(requestTo("https://discord.test/guilds/324/scheduled-events/e1")).andRespond(withSuccess())
        discord.expect(requestTo("https://discord.test/guilds/324/scheduled-events/e2")).andRespond(withStatus(HttpStatus.NOT_FOUND))
        discord.expect(requestTo("https://discord.test/channels/111/messages/m4")).andRespond(withStatus(HttpStatus.FORBIDDEN))

        assertThat(publisher.stillPosted("events-info", "222/m1")).isEqualTo("222/m1")
        assertThat(publisher.stillPosted("events-info", "m2")).isEqualTo("111/m2")
        assertThat(publisher.stillPosted("events-info", "111/m3")).isNull()
        assertThat(publisher.stillListed("e1")).isTrue()
        assertThat(publisher.stillListed("e2")).isFalse()
        assertThatThrownBy { publisher.stillPosted("events-info", "111/m4") }
            .hasMessage("The bot may not read #events-info: it needs View Channel and Read Message History there.")
        discord.verify()
    }

    @Test
    fun `removes a message and a Discord event that are still there`() {
        publisher.delete("events-info", "m1")
        publisher.deleteDiscordEvent("e1")

        verify(api).deleteMessage("111", "m1")
        verify(api).deleteGuildScheduledEvent("324", "e1")
    }

    @Test
    fun `refuses a channel the server does not have, naming it`() {
        assertThatThrownBy { publisher.post("events-lobby", post) }
            .isInstanceOf(ExplainedJobFailure::class.java)
            .hasMessage("The Discord server has no text channel called #events-lobby.")
    }

    @Test
    fun `says which permission the bot lacks for what it was doing`() {
        whenever(api.deleteMessage("111", "m1")).thenThrow(HttpClientErrorException(HttpStatus.FORBIDDEN))
        discord.expect(requestTo("https://discord.test/channels/111/messages")).andRespond(withStatus(HttpStatus.FORBIDDEN))
        whenever(api.deleteGuildScheduledEvent("324", "e1")).thenThrow(HttpClientErrorException(HttpStatus.FORBIDDEN))
        discord.expect(requestTo("https://discord.test/channels/111/messages?limit=100")).andRespond(withStatus(HttpStatus.FORBIDDEN))
        discord.expect(requestTo("https://discord.test/guilds/324/scheduled-events")).andRespond(withStatus(HttpStatus.FORBIDDEN))

        assertThatThrownBy { publisher.post("events-info", post) }
            .isInstanceOf(ExplainedJobFailure::class.java)
            .hasMessage(
                "The bot may not post in #events-info: it needs Send Messages, Embed Links, Attach Files and Mention All Roles there.",
            ).hasCauseInstanceOf(HttpClientErrorException::class.java)
        assertThatThrownBy { publisher.findPosts("events-info", "https://site/events/42") }
            .hasMessage("The bot may not read #events-info: it needs View Channel and Read Message History there.")
        assertThatThrownBy { publisher.delete("events-info", "m1") }
            .hasMessage("The bot may not remove its post in #events-info: it needs View Channel there.")
        assertThatThrownBy { publisher.findDiscordEvents("More on the site: https://site/events/42") }
            .hasMessage("The bot may not keep the server's Events list: it needs Create Events.")
        assertThatThrownBy { publisher.deleteDiscordEvent("e1") }
            .hasMessage("The bot may not keep the server's Events list: it needs Create Events.")
        discord.verify()
    }

    @Test
    fun `says Discord refuses a post or a Discord event as too long, and passes on any other refusal`() {
        val tooLong = """{"code": 50035, "errors": {"description": {"_errors": [{"code": "BASE_TYPE_MAX_LENGTH"}]}}}"""
        val textTooLong = """{"errors": {"components": {"_errors": [{"code": "COMPONENT_DISPLAYABLE_TEXT_SIZE_EXCEEDED"}]}}}"""
        val displayTooLong = """{"errors": {"components": {"0": {"content": {"_errors": [{"code": "BASE_TYPE_BAD_LENGTH"}]}}}}}"""
        discord
            .expect(requestTo("https://discord.test/channels/111/messages"))
            .andRespond(withBadRequest().body(textTooLong).contentType(MediaType.APPLICATION_JSON))
        discord
            .expect(requestTo("https://discord.test/channels/111/messages/m1"))
            .andRespond(withBadRequest().body(displayTooLong).contentType(MediaType.APPLICATION_JSON))
        discord
            .expect(requestTo("https://discord.test/guilds/324/scheduled-events"))
            .andRespond(withBadRequest().body(tooLong).contentType(MediaType.APPLICATION_JSON))
        discord.expect(requestTo("https://discord.test/guilds/324/scheduled-events")).andRespond(withBadRequest())

        assertThatThrownBy { publisher.post("events-info", post.copy(links = listOf(DiscordLink("Sign up", "https://site/events/42")))) }
            .hasMessage("Discord refuses the #events-info post as too long.")
        assertThatThrownBy { publisher.edit("events-info", "m1", post) }
            .hasMessage("Discord refuses the #events-info post as too long.")
        assertThatThrownBy { publisher.createDiscordEvent(listing.copy(cover = null)) }
            .hasMessage("Discord refuses the Discord event as too long.")
        assertThatThrownBy { publisher.createDiscordEvent(listing.copy(cover = null)) }
            .isInstanceOf(HttpClientErrorException::class.java)
        discord.verify()
    }

    @Test
    fun `answers an edit to a Discord event Discord has ended as gone, without trying again with no cover`() {
        val finished = """{"message": "Cannot update a finished event", "code": 180000}"""
        discord
            .expect(requestTo("https://discord.test/guilds/324/scheduled-events/e1"))
            .andRespond(withBadRequest().body(finished).contentType(MediaType.APPLICATION_JSON))

        assertThat(publisher.updateDiscordEvent("e1", listing)).isFalse()
        discord.verify()
    }

    @Test
    fun `says the server's Events list is full where Discord refuses one more Discord event`() {
        val full = """{"message": "Maximum number of uncompleted guild scheduled events reached (100)", "code": 30038}"""
        discord
            .expect(requestTo("https://discord.test/guilds/324/scheduled-events"))
            .andRespond(withBadRequest().body(full).contentType(MediaType.APPLICATION_JSON))

        assertThatThrownBy { publisher.createDiscordEvent(listing.copy(cover = null)) }
            .isInstanceOf(ExplainedJobFailure::class.java)
            .hasMessage("The server's Events list is full: Discord holds at most 100 Discord events that have not ended.")
        discord.verify()
    }

    @Test
    fun `says Discord is unavailable when it answers with an error of its own or cannot be reached`() {
        discord.expect(requestTo("https://discord.test/guilds/324/scheduled-events")).andRespond(withServerError())
        discord
            .expect(requestTo("https://discord.test/guilds/324/scheduled-events"))
            .andRespond { throw java.io.IOException("Connection refused") }

        assertThatThrownBy { publisher.findDiscordEvents("line") }.hasMessage("Discord is unavailable.")
        assertThatThrownBy { publisher.findDiscordEvents("line") }
            .isInstanceOf(ExplainedJobFailure::class.java)
            .hasMessage("Discord is unavailable.")
            .hasCauseInstanceOf(ResourceAccessException::class.java)
        discord.verify()
    }

    @Test
    fun `lists an external Discord event at its place and times, reading back only its ID, and brings it up to date`() {
        // Discord answers a cover-less Discord event with a null image, which the generated model refuses.
        discord
            .expect(requestTo("https://discord.test/guilds/324/scheduled-events"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.entity_metadata.location").value("Pakhuis"))
            .andExpect(jsonPath("$.entity_type").value(3))
            .andExpect(jsonPath("$.privacy_level").value(2))
            .andExpect(jsonPath("$.image").value("data:x"))
            .andRespond(withSuccess("""{"id": "e1", "image": null}""", MediaType.APPLICATION_JSON))
        discord
            .expect(requestTo("https://discord.test/guilds/324/scheduled-events/e1"))
            .andExpect(method(HttpMethod.PATCH))
            .andExpect(jsonPath("$.name").value("LAN party"))
            .andRespond(withSuccess("""{"id": "e1", "image": null}""", MediaType.APPLICATION_JSON))

        assertThat(publisher.createDiscordEvent(listing)).isEqualTo("e1")
        publisher.updateDiscordEvent("e1", listing)

        discord.verify()
    }

    @Test
    fun `lists and relists without the cover where Discord refuses it, and gives up on anything else`() {
        discord.expect(requestTo("https://discord.test/guilds/324/scheduled-events")).andRespond(withBadRequest())
        discord
            .expect(requestTo("https://discord.test/guilds/324/scheduled-events"))
            .andExpect(jsonPath("$.image").doesNotExist())
            .andRespond(withSuccess("""{"id": "e1"}""", MediaType.APPLICATION_JSON))
        discord.expect(requestTo("https://discord.test/guilds/324/scheduled-events/e1")).andRespond(withBadRequest())
        discord
            .expect(requestTo("https://discord.test/guilds/324/scheduled-events/e1"))
            .andExpect(jsonPath("$.image").value(nullValue()))
            .andRespond(withSuccess("""{"id": "e1"}""", MediaType.APPLICATION_JSON))
        discord.expect(requestTo("https://discord.test/guilds/324/scheduled-events")).andRespond(withBadRequest())
        discord.expect(requestTo("https://discord.test/guilds/324/scheduled-events")).andRespond(withServerError())

        assertThat(publisher.createDiscordEvent(listing)).isEqualTo("e1")
        publisher.updateDiscordEvent("e1", listing)
        assertThatThrownBy { publisher.createDiscordEvent(listing.copy(cover = null)) }
            .isInstanceOf(HttpClientErrorException::class.java)
        assertThatThrownBy { publisher.createDiscordEvent(listing) }
            .isInstanceOf(ExplainedJobFailure::class.java)
            .hasCauseInstanceOf(HttpServerErrorException::class.java)
        discord.verify()
    }

    @Test
    fun `finds this bot's own posts linking a page by a button or an embed, not another bot's, and Discord events naming it on a line`() {
        discord
            .expect(requestTo("https://discord.test/channels/111/messages?limit=100"))
            .andRespond(
                withSuccess(
                    """
                    [
                      {"id": "m5", "author": {"id": "900", "bot": true}, "embeds": [], "components": [
                        {"type": 10, "content": "## LAN party"},
                        {"type": 1, "components": [{"type": 2, "style": 5, "url": "https://site/events/42"}]}
                      ]},
                      {"id": "m4", "author": {"id": "900", "bot": true}, "embeds": [{"url": "https://site/events/42"}]},
                      {"id": "m3", "author": {"id": "700", "bot": true}, "embeds": [{"url": "https://site/events/42"}]},
                      {"id": "m2", "author": {"id": "701", "bot": false}, "embeds": [{"url": "https://site/events/42"}]},
                      {"id": "m1", "author": {"id": "900", "bot": true}, "embeds": [{"url": "https://site/events/421"}], "components": [
                        {"type": 1, "components": [{"type": 2, "style": 5, "url": "https://site/events/421#signup"}]}
                      ]},
                      {"id": "m0", "author": {"id": "900", "bot": true}}
                    ]
                    """,
                    MediaType.APPLICATION_JSON,
                ),
            )
        discord
            .expect(requestTo("https://discord.test/users/@me"))
            .andRespond(withSuccess("""{"id": "900", "bot": true}""", MediaType.APPLICATION_JSON))
        discord
            .expect(requestTo("https://discord.test/guilds/324/scheduled-events"))
            .andRespond(
                withSuccess(
                    """
                    [
                      {"id": "e2", "description": "Bring a rig.\n\nMore on the site: https://site/events/42\nSign up: x"},
                      {"id": "e1", "description": "More on the site: https://site/events/421"},
                      {"id": "e0", "description": null}
                    ]
                    """,
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertThat(publisher.findPosts("events-info", "https://site/events/42")).containsExactly("111/m5", "111/m4")
        assertThat(publisher.findDiscordEvents("More on the site: https://site/events/42")).containsExactly("e2")
        discord.verify()
    }

    @Test
    fun `passes a rate limit on rather than dropping the banner, and says when the message was removed by hand`() {
        discord.expect(requestTo("https://discord.test/channels/111/messages")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS))
        discord.expect(requestTo("https://discord.test/channels/111/messages/m9")).andRespond(withStatus(HttpStatus.NOT_FOUND))
        discord
            .expect(requestTo("https://discord.test/channels/111/messages/m1"))
            .andRespond(withSuccess("""{"id": "m1"}""", MediaType.APPLICATION_JSON))

        assertThatThrownBy { publisher.post("events-info", post.copy(banner = banner)) }
            .hasMessage("Discord is rate limiting the bot.")
            .hasCauseInstanceOf(HttpClientErrorException.TooManyRequests::class.java)
        assertThat(publisher.edit("events-info", "m9", post)).isFalse()
        assertThat(publisher.edit("events-info", "m1", post)).isTrue()
        discord.verify()
    }

    @Test
    fun `clears a cover taken off, leaves a start in the past alone, and says when the Discord event was removed by hand`() {
        discord
            .expect(requestTo("https://discord.test/guilds/324/scheduled-events/e1"))
            .andExpect(jsonPath("$.image").value(nullValue()))
            .andExpect(jsonPath("$.scheduled_start_time").doesNotExist())
            .andExpect(jsonPath("$.scheduled_end_time").exists())
            .andRespond(withSuccess("""{"id": "e1"}""", MediaType.APPLICATION_JSON))
        discord.expect(requestTo("https://discord.test/guilds/324/scheduled-events/e2")).andRespond(withStatus(HttpStatus.NOT_FOUND))

        assertThat(publisher.updateDiscordEvent("e1", listing.copy(cover = null, start = null))).isTrue()
        assertThat(publisher.updateDiscordEvent("e2", listing.copy(cover = null))).isFalse()
        assertThatThrownBy { publisher.createDiscordEvent(listing.copy(start = null)) }.hasMessageContaining("before it starts")
        discord.verify()
    }
}
