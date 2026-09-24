package net.blueshell.api.discord.web

import net.blueshell.api.discord.domain.DiscordLive
import net.blueshell.api.discord.domain.DiscordLiveService
import net.blueshell.api.discord.domain.LiveRoom
import net.blueshell.api.discord.domain.VoicePerson
import net.blueshell.api.discord.domain.ViewerRoomService
import net.blueshell.api.discord.domain.ViewerRooms
import net.blueshell.api.discord.domain.VoiceRoom
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.http.HttpStatus

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

        val response = DiscordController(service, mock()).live()

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

        assertThat(DiscordController(service, mock()).live().statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
    }

    @Test
    fun `answers what the viewer's own member may join, uncached, or 503 without a bot`() {
        val rooms: ViewerRoomService = mock { on { rooms() } doReturn ViewerRooms(linked = true, joinable = setOf("12", "11")) }

        val response = DiscordController(mock(), rooms).mine()

        assertThat(response.body).isEqualTo(DiscordViewerRoomsResponse(linked = true, joinable = listOf("11", "12")))
        assertThat(response.body!!.let { it.linked to it.joinable }).isEqualTo(true to listOf("11", "12"))
        assertThat(response.headers.cacheControl).isEqualTo("no-store")

        val offline: ViewerRoomService = mock { on { rooms() } doReturn null }
        assertThat(DiscordController(mock(), offline).mine().statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
    }
}

