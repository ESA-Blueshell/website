package net.blueshell.api.discord.domain

import net.blueshell.api.sync.api.DiscordEmbed
import net.blueshell.api.sync.api.DiscordEventListing
import net.blueshell.api.sync.api.DiscordImage
import net.blueshell.api.sync.api.DiscordPost
import net.blueshell.clients.discord.api.DiscordApi
import net.blueshell.clients.discord.model.MessageCreateRequest
import net.blueshell.clients.discord.model.MessageEditRequestPartial
import net.blueshell.clients.discord.model.MessageResponse
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.nullValue
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider
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
    private val provided: ObjectProvider<DoorSource> = mock { on { ifAvailable } doReturn doors }
    private val mapper = JsonMapper.builder().build()
    private val builder = RestClient.builder().baseUrl("https://discord.test")
    private val discord = MockRestServiceServer.bindTo(builder).build()
    private val publisher = BotPublisher(api, builder.build(), mapper, provided, "324")

    private val banner = DiscordImage("banner.webp", "image/webp", byteArrayOf(1, 2, 3))

    private val post =
        DiscordPost(
            pingedRoleIds = listOf("901", "902"),
            embed =
                DiscordEmbed(
                    title = "LAN party",
                    url = "https://site/events/42",
                    description = "Bring a rig.",
                    fields = listOf("When" to "soon"),
                ),
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
    fun `posts in the channel of that name, naming and notifying only the pinged roles`() {
        val message: MessageResponse = mock { on { id } doReturn "m1" }
        whenever(api.createMessage(eq("111"), any())).thenReturn(message)

        assertThat(publisher.post("events-info", post)).isEqualTo("m1")

        val sent = argumentCaptor<MessageCreateRequest>()
        verify(api).createMessage(eq("111"), sent.capture())
        assertThat(sent.firstValue.content).isEqualTo("<@&901> <@&902>")
        assertThat(sent.firstValue.allowedMentions!!.roles).containsExactlyInAnyOrder("901", "902")
        assertThat(sent.firstValue.allowedMentions!!.parse).isEmpty()
        val embed = sent.firstValue.embeds!!.single()
        assertThat(embed.title).isEqualTo("LAN party")
        assertThat(embed.image).isNull()
        assertThat(sent.firstValue.attachments).isEmpty()
        assertThat(embed.fields!!.single().let { it.name to it.value }).isEqualTo("When" to "soon")
    }

    @Test
    fun `edits without notifying anybody, and posts no mentions where no role is pinged`() {
        publisher.edit("events-info", "m1", post.copy(pingedRoleIds = emptyList()))

        val sent = argumentCaptor<MessageEditRequestPartial>()
        verify(api).updateMessage(eq("111"), eq("m1"), sent.capture())
        assertThat(sent.firstValue.content).isEmpty()
        assertThat(sent.firstValue.allowedMentions!!.parse).isEmpty()
        assertThat(sent.firstValue.allowedMentions!!.roles).isNull()
        assertThat(sent.firstValue.attachments).isEmpty()
    }

    @Test
    fun `sends the banner as a file above the embed, on a post and on an edit`() {
        discord
            .expect(requestTo("https://discord.test/channels/111/messages"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.CONTENT_TYPE, containsString("multipart/form-data")))
            .andExpect(content().string(containsString("filename=\"banner.webp\"")))
            .andExpect(content().string(not(containsString("attachment://"))))
            .andExpect(content().string(containsString("<@&901> <@&902>")))
            .andRespond(withSuccess("""{"id": "m1"}""", MediaType.APPLICATION_JSON))
        discord
            .expect(requestTo("https://discord.test/channels/111/messages/m1"))
            .andExpect(method(HttpMethod.PATCH))
            .andExpect(content().string(containsString("filename=\"banner.webp\"")))
            .andRespond(withSuccess("""{"id": "m1"}""", MediaType.APPLICATION_JSON))

        assertThat(publisher.post("events-info", post.copy(banner = banner))).isEqualTo("m1")
        publisher.edit("events-info", "m1", post.copy(banner = banner))

        discord.verify()
        verifyNoInteractions(api)
    }

    @Test
    fun `posts and edits without the banner where Discord refuses the file, and gives up on anything else`() {
        val message: MessageResponse = mock { on { id } doReturn "m1" }
        whenever(api.createMessage(eq("111"), any())).thenReturn(message)
        discord.expect(requestTo("https://discord.test/channels/111/messages")).andRespond(withStatus(HttpStatus.CONTENT_TOO_LARGE))
        discord.expect(requestTo("https://discord.test/channels/111/messages/m1")).andRespond(withStatus(HttpStatus.CONTENT_TOO_LARGE))
        discord.expect(requestTo("https://discord.test/channels/111/messages")).andRespond(withServerError())

        assertThat(publisher.post("events-info", post.copy(banner = banner))).isEqualTo("m1")
        publisher.edit("events-info", "m1", post.copy(banner = banner))
        assertThatThrownBy { publisher.post("events-info", post.copy(banner = banner)) }
            .isInstanceOf(HttpServerErrorException::class.java)

        val sent = argumentCaptor<MessageCreateRequest>()
        verify(api).createMessage(eq("111"), sent.capture())
        assertThat(sent.firstValue.embeds!!.single().image).isNull()
        verify(api).updateMessage(eq("111"), eq("m1"), any())
        whenever(api.createMessage(eq("111"), any())).thenThrow(HttpClientErrorException(HttpStatus.BAD_REQUEST))
        assertThatThrownBy { publisher.post("events-info", post) }.isInstanceOf(HttpClientErrorException::class.java)
    }

    @Test
    fun `counts a message or Discord event already gone as removed, and passes on anything else`() {
        whenever(api.deleteMessage("111", "m1")).thenThrow(HttpClientErrorException(HttpStatus.NOT_FOUND))
        publisher.delete("events-info", "m1")

        whenever(api.deleteGuildScheduledEvent("324", "e1")).thenThrow(HttpClientErrorException(HttpStatus.FORBIDDEN))
        assertThatThrownBy { publisher.deleteDiscordEvent("e1") }.isInstanceOf(HttpClientErrorException::class.java)
    }

    @Test
    fun `removes a message and a Discord event that are still there`() {
        publisher.delete("events-info", "m1")
        publisher.deleteDiscordEvent("e1")

        verify(api).deleteMessage("111", "m1")
        verify(api).deleteGuildScheduledEvent("324", "e1")
    }

    @Test
    fun `refuses a channel the server does not have`() {
        assertThatThrownBy { publisher.post("events-lobby", post) }.hasMessageContaining("events-lobby")
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
            .isInstanceOf(HttpServerErrorException::class.java)
        discord.verify()
    }

    @Test
    fun `finds the bot's own posts linking a page, and Discord events naming it on a line`() {
        discord
            .expect(requestTo("https://discord.test/channels/111/messages?limit=100"))
            .andRespond(
                withSuccess(
                    """
                    [
                      {"id": "m3", "author": {"bot": true}, "embeds": [{"url": "https://site/events/42"}]},
                      {"id": "m2", "author": {"bot": false}, "embeds": [{"url": "https://site/events/42"}]},
                      {"id": "m1", "author": {"bot": true}, "embeds": [{"url": "https://site/events/421"}]},
                      {"id": "m0", "author": {"bot": true}}
                    ]
                    """,
                    MediaType.APPLICATION_JSON,
                ),
            )
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

        assertThat(publisher.findPosts("events-info", "https://site/events/42")).containsExactly("m3")
        assertThat(publisher.findDiscordEvents("More on the site: https://site/events/42")).containsExactly("e2")
        discord.verify()
    }

    @Test
    fun `passes a rate limit on rather than dropping the banner, and says when the message was removed by hand`() {
        discord.expect(requestTo("https://discord.test/channels/111/messages")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS))
        whenever(api.updateMessage(eq("111"), eq("m9"), any())).thenThrow(HttpClientErrorException(HttpStatus.NOT_FOUND))

        assertThatThrownBy { publisher.post("events-info", post.copy(banner = banner)) }
            .isInstanceOf(HttpClientErrorException.TooManyRequests::class.java)
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
