package net.blueshell.api.discord.web

import net.blueshell.api.discord.domain.DiscordDoor
import net.blueshell.api.discord.domain.DiscordDoorService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.http.HttpStatus

class DiscordDoorControllerTest {
    private val doors: DiscordDoorService =
        mock {
            on { invite(DiscordDoor.BOARD) } doReturn "https://discord.gg/board"
            on { channel(DiscordDoor.SUGGESTIONS) } doReturn "https://discord.com/channels/324/102"
        }
    private val controller = DiscordDoorController(doors)

    @Test
    fun `redirects to the door's invite or channel, cacheable for a few minutes`() {
        val invite = controller.invite("board")
        assertThat(invite.statusCode).isEqualTo(HttpStatus.FOUND)
        assertThat(invite.headers.location.toString()).isEqualTo("https://discord.gg/board")
        assertThat(invite.headers.cacheControl).isEqualTo("max-age=300, public")

        assertThat(controller.channel("suggestions").headers.location.toString()).isEqualTo("https://discord.com/channels/324/102")
    }

    @Test
    fun `answers 404 for a door there is not`() {
        assertThat(controller.invite("lobby").statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(controller.channel("lobby").statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }
}
