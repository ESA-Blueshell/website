package net.blueshell.api.discord.domain

import net.blueshell.api.sync.api.DiscordEmbed
import net.blueshell.api.sync.api.DiscordEventListing
import net.blueshell.api.sync.api.DiscordPost
import net.blueshell.clients.discord.api.DiscordApi
import net.blueshell.clients.discord.model.CreateGuildScheduledEvent200Response
import net.blueshell.clients.discord.model.CreateGuildScheduledEventRequest
import net.blueshell.clients.discord.model.MessageCreateRequest
import net.blueshell.clients.discord.model.MessageEditRequestPartial
import net.blueshell.clients.discord.model.MessageResponse
import net.blueshell.clients.discord.model.UpdateGuildScheduledEventRequest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import java.time.Instant

class BotPublisherTest {
    private val api: DiscordApi = mock()
    private val doors: DoorSource =
        object : DoorSource {
            override fun textRooms() = listOf(TextRoom("111", "324", "📣events-info"))

            override fun invite(channelId: String): String? = null
        }
    private val provided: ObjectProvider<DoorSource> = mock { on { ifAvailable } doReturn doors }
    private val publisher = BotPublisher(api, provided, "324")

    private val post =
        DiscordPost(
            pingedRoleIds = listOf("901", "902"),
            embed =
                DiscordEmbed(
                    title = "LAN party",
                    url = "https://site/events/42",
                    description = "Bring a rig.",
                    fields = listOf("When" to "soon"),
                    imageUrl = "https://site/banner.webp",
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
        assertThat(embed.image!!.url.toString()).isEqualTo("https://site/banner.webp")
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
    fun `lists an external Discord event at its place and times, and brings it up to date`() {
        val created: CreateGuildScheduledEvent200Response = mock { on { id } doReturn "e1" }
        whenever(api.createGuildScheduledEvent(eq("324"), any())).thenReturn(created)

        assertThat(publisher.createDiscordEvent(listing)).isEqualTo("e1")
        publisher.updateDiscordEvent("e1", listing)

        val sent = argumentCaptor<CreateGuildScheduledEventRequest>()
        verify(api).createGuildScheduledEvent(eq("324"), sent.capture())
        assertThat(sent.firstValue.entityMetadata).isEqualTo(mapOf("location" to "Pakhuis"))
        assertThat(sent.firstValue.image).isEqualTo("data:x")
        assertThat(sent.firstValue.scheduledEndTime!!.toInstant()).isEqualTo(listing.end)
        val updated = argumentCaptor<UpdateGuildScheduledEventRequest>()
        verify(api).updateGuildScheduledEvent(eq("324"), eq("e1"), updated.capture())
        assertThat(updated.firstValue.name).isEqualTo("LAN party")
    }

    @Test
    fun `lists and relists without the cover where Discord refuses it, and gives up on anything else`() {
        val created: CreateGuildScheduledEvent200Response = mock { on { id } doReturn "e1" }
        whenever(api.createGuildScheduledEvent(eq("324"), any()))
            .thenThrow(HttpClientErrorException(HttpStatus.BAD_REQUEST))
            .thenReturn(created)
        whenever(api.updateGuildScheduledEvent(eq("324"), eq("e1"), any()))
            .thenThrow(HttpClientErrorException(HttpStatus.BAD_REQUEST))
            .thenReturn(mock())

        assertThat(publisher.createDiscordEvent(listing)).isEqualTo("e1")
        publisher.updateDiscordEvent("e1", listing)

        val sent = argumentCaptor<CreateGuildScheduledEventRequest>()
        verify(api, times(2)).createGuildScheduledEvent(eq("324"), sent.capture())
        assertThat(sent.allValues.map { it.image }).containsExactly("data:x", null)
        val updated = argumentCaptor<UpdateGuildScheduledEventRequest>()
        verify(api, times(2)).updateGuildScheduledEvent(eq("324"), eq("e1"), updated.capture())
        assertThat(updated.allValues.map { it.image }).containsExactly("data:x", null)

        val down: DiscordApi =
            mock { on { createGuildScheduledEvent(eq("324"), any()) } doThrow HttpClientErrorException(HttpStatus.BAD_REQUEST) }
        assertThatThrownBy { BotPublisher(down, provided, "324").createDiscordEvent(listing.copy(cover = null)) }
            .isInstanceOf(HttpClientErrorException::class.java)
        val broken: DiscordApi =
            mock { on { createGuildScheduledEvent(eq("324"), any()) } doThrow HttpServerErrorException(HttpStatus.BAD_GATEWAY) }
        assertThatThrownBy { BotPublisher(broken, provided, "324").createDiscordEvent(listing) }
            .isInstanceOf(HttpServerErrorException::class.java)
    }
}

