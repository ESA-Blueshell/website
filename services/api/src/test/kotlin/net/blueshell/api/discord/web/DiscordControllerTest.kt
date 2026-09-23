package net.blueshell.api.discord.web

import net.blueshell.api.discord.domain.DiscordLive
import net.blueshell.api.discord.domain.DiscordLiveService
import net.blueshell.api.discord.domain.LiveRoom
import net.blueshell.api.discord.domain.VoicePerson
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

        val response = DiscordController(service).live()

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

        assertThat(DiscordController(service).live().statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
    }
}
