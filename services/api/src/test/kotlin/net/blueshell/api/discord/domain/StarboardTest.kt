package net.blueshell.api.discord.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private const val JUMP = "https://discord.com/channels/324/611/1552233582498676818"

class StarboardTest {
    private val mapper = JsonMapper.builder().build()

    // The layout the starboard bot writes, read off a forwarded repost; names and IDs are made up.
    private fun repost(
        stars: String = "💫 **12** <#611>",
        author: String = "The Old Man  - Joris",
        description: String? = "Soon to be released events page redesigns:",
        image: String? = "https://cdn.discordapp.com/attachments/611/1/image.png",
        timestamp: String = "2026-09-23T08:22:05.173000+00:00",
    ): Map<String, Any?> {
        val embed =
            buildMap {
                put("type", "rich")
                description?.let { put("description", it) }
                put("timestamp", timestamp)
                put("fields", listOf(mapOf("name" to "Source", "value" to "[Jump!]($JUMP)")))
                put("author", mapOf("name" to author, "icon_url" to "https://cdn.discordapp.com/avatars/120/fa7b.png"))
                image?.let { put("image", mapOf("url" to it)) }
                put("footer", mapOf("text" to "1552233582498676818"))
            }
        return mapOf("content" to stars, "embeds" to listOf(embed))
    }

    @Test
    fun `reads a repost into the message it stars`() {
        assertThat(starredMessageOf(repost())).isEqualTo(
            StarredMessage(
                id = "1552233582498676818",
                authorName = "The Old Man",
                authorNickname = "Joris",
                avatar = "https://cdn.discordapp.com/avatars/120/fa7b.png",
                text = "Soon to be released events page redesigns:",
                image = "https://cdn.discordapp.com/attachments/611/1/image.png",
                stars = 12,
                channelId = "611",
                href = "https://discord.com/channels/324/611/1552233582498676818",
                postedAt = Instant.parse("2026-09-23T08:22:05.173Z"),
            ),
        )
    }

    @Test
    fun `reads an author without a nickname, a picture without words, and every tier of star`() {
        val bare = starredMessageOf(repost(stars = "🌟 **5** <#758>", author = "cawalive", description = null, image = null))!!

        assertThat(bare.authorName).isEqualTo("cawalive")
        assertThat(bare.authorNickname).isNull()
        assertThat(bare.text).isNull()
        assertThat(bare.image).isNull()
        assertThat(bare.stars to bare.channelId).isEqualTo(5 to "758")
    }

    @Test
    fun `reads anything that is not a repost as nothing`() {
        assertThat(starredMessageOf(mapOf("content" to "nice one", "embeds" to emptyList<Any>()))).isNull()
        assertThat(starredMessageOf(repost() + ("embeds" to emptyList<Any>()))).isNull()
        assertThat(starredMessageOf(repost(stars = "no count here <#611>"))).isNull()
    }

    private val now = Instant.parse("2026-09-30T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private fun starred(
        id: String,
        stars: Int = 12,
        channelId: String = "611",
        daysOld: Long = 1,
    ) = starredMessageOf(repost(stars = "⭐ **$stars** <#$channelId>"))!!.copy(id = id, postedAt = now.minusSeconds(daysOld * 86_400))

    private fun <T : Any> provided(bean: T?): ObjectProvider<T> = mock { on { ifAvailable } doReturn bean }

    private val openChannels: DiscordChannelDirectory =
        mock { on { open() } doReturn listOf(DiscordChannel("611", "general", null), DiscordChannel("758", "events-calendar", null)) }

    @Test
    fun `shows messages with three stars or more from the last thirty days, newest first, only from open channels`() {
        val source =
            StarboardSource {
                listOf(
                    starred("older", daysOld = 3),
                    starred("newest", channelId = "758"),
                    starred("two-stars", stars = 2),
                    starred("three-stars", stars = 3, daysOld = 2),
                    starred("month-old", daysOld = 31),
                    starred("members-only", channelId = "999"),
                )
            }

        val entries = StarboardService(provided(source), openChannels, clock).entries()!!

        assertThat(entries.map { it.message.id }).containsExactly("newest", "three-stars", "older")
        assertThat(entries.first().channel).isEqualTo("events-calendar")
    }

    @Test
    fun `keeps what it read rather than asking Discord on every page view, and has nothing without a bot`() {
        val source: StarboardSource = mock { on { recent() } doReturn listOf(starred("one")) }
        val service = StarboardService(provided(source), openChannels, clock)

        service.entries()
        service.entries()

        verify(source, times(1)).recent()
        assertThat(StarboardService(provided(null), openChannels).entries()).isNull()
    }

    @Test
    fun `reads the starboard channel by name, and nothing while the gateway has no such channel`() {
        val builder = RestClient.builder().baseUrl("https://discord.test")
        val discord = MockRestServiceServer.bindTo(builder).build()
        val rooms = mutableListOf(TextRoom("777", "324", "⭐starboard"))
        val doors =
            object : DoorSource {
                override fun textRooms() = rooms

                override fun invite(channelId: String): String? = null
            }
        discord
            .expect(requestTo("https://discord.test/channels/777/messages?limit=100"))
            .andRespond(withSuccess(mapper.writeValueAsString(listOf(repost(), mapOf("content" to "hi"))), MediaType.APPLICATION_JSON))

        val source = RestStarboardSource(builder.build(), doors, "starboard")

        assertThat(source.recent()!!.map { it.id }).containsExactly("1552233582498676818")
        rooms.clear()
        assertThat(source.recent()).isNull()
        discord.verify()
    }
}
