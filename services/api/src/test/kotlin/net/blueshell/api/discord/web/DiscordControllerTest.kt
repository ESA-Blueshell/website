package net.blueshell.api.discord.web

import net.blueshell.api.discord.domain.DiscordLive
import net.blueshell.api.discord.domain.DiscordLiveService
import net.blueshell.api.discord.domain.LiveRoom
import net.blueshell.api.discord.domain.StarboardEntry
import net.blueshell.api.discord.domain.StarboardService
import net.blueshell.api.discord.domain.StarredMessage
import net.blueshell.api.discord.domain.ViewerRoomService
import net.blueshell.api.discord.domain.ViewerRooms
import net.blueshell.api.discord.domain.VoicePerson
import net.blueshell.api.discord.domain.VoiceRoom
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.http.HttpStatus
import java.time.Instant

class DiscordControllerTest {
    @Test
    fun `answers the live server, cacheable for a moment`() {
        val live =
            DiscordLive(
                server = "Blueshell",
                online = 269,
                members = 1199,
                rooms =
                    listOf(
                        LiveRoom(
                            VoiceRoom("2", "General", 1, false, listOf(VoicePerson("Emma", null))),
                            "https://discord.com/channels/324/2",
                        ),
                    ),
            )
        val service: DiscordLiveService = mock { on { live() } doReturn live }

        val response = DiscordController(service, mock(), mock()).live()

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.headers.cacheControl).contains("max-age=15")
        assertThat(response.body).isEqualTo(
            DiscordLiveResponse(
                server = "Blueshell",
                online = 269,
                members = 1199,
                rooms =
                    listOf(
                        DiscordVoiceRoomResponse(
                            "2",
                            "General",
                            false,
                            "https://discord.com/channels/324/2",
                            listOf(DiscordVoicePersonResponse("Emma", null)),
                        ),
                    ),
            ),
        )
    }

    @Test
    fun `says unavailable without a bot, so the band falls back to the public widget`() {
        val service: DiscordLiveService = mock { on { live() } doReturn null }

        assertThat(DiscordController(service, mock(), mock()).live().statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
    }

    @Test
    fun `answers what the viewer's own member may join, uncached, or 503 without a bot`() {
        val rooms: ViewerRoomService = mock { on { rooms() } doReturn ViewerRooms(linked = true, joinable = setOf("12", "11")) }

        val response = DiscordController(mock(), rooms, mock()).mine()

        assertThat(response.body).isEqualTo(DiscordViewerRoomsResponse(linked = true, joinable = listOf("11", "12")))
        assertThat(response.body!!.let { it.linked to it.joinable }).isEqualTo(true to listOf("11", "12"))
        assertThat(response.headers.cacheControl).isEqualTo("no-store")

        val offline: ViewerRoomService = mock { on { rooms() } doReturn null }
        assertThat(DiscordController(mock(), offline, mock()).mine().statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
    }

    @Test
    fun `answers the starboard, cacheable for a few minutes, or 503 without a bot`() {
        val message =
            StarredMessage(
                id = "9",
                authorName = "The Old Man",
                authorNickname = "Joris",
                avatar = null,
                text = "hi",
                image = null,
                stars = 12,
                channelId = "611",
                href = "https://discord.com/channels/324/611/9",
                postedAt = Instant.parse("2026-09-23T08:22:05Z"),
            )
        val starboard: StarboardService = mock { on { entries() } doReturn listOf(StarboardEntry(message, "general")) }

        val response = DiscordController(mock(), mock(), starboard).starboard()

        assertThat(response.headers.cacheControl).contains("max-age=300").contains("private")
        // Read field by field: the getters are what the serialiser calls.
        val shown = response.body!!.single()
        assertThat(listOf(shown.id, shown.authorName, shown.authorNickname, shown.avatar, shown.text, shown.image))
            .containsExactly("9", "The Old Man", "Joris", null, "hi", null)
        assertThat(listOf(shown.stars, shown.channel, shown.href, shown.postedAt))
            .containsExactly(12, "general", "https://discord.com/channels/324/611/9", Instant.parse("2026-09-23T08:22:05Z"))

        val offline: StarboardService = mock { on { entries() } doReturn null }
        assertThat(DiscordController(mock(), mock(), offline).starboard().statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
    }
}
