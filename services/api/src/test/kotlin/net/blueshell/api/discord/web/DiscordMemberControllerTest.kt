package net.blueshell.api.discord.web

import net.blueshell.api.discord.domain.DiscordMember
import net.blueshell.api.discord.domain.DiscordMemberDirectory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.http.HttpStatus

class DiscordMemberControllerTest {
    private val directory: DiscordMemberDirectory =
        mock {
            on { search("nel") } doReturn listOf(DiscordMember("803", "Nelly B", "nelly", "https://cdn/nelly.png"))
            on { search("off") } doReturn null
            on { unclaimed() } doReturn listOf(DiscordMember("804", "Anna", "anna", "https://cdn/anna.png"))
        }
    private val controller = DiscordMemberController(directory)

    @Test
    fun `answers the members found`() {
        val answer = controller.search("nel")

        assertThat(answer.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(answer.body).containsExactly(DiscordMemberResponse("803", "Nelly B", "nelly", "https://cdn/nelly.png"))
    }

    @Test
    fun `answers 503 without a bot, so the picker falls back to typing`() {
        assertThat(controller.search("off").statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
    }

    @Test
    fun `answers everybody nobody has linked yet, or 503 without a bot`() {
        assertThat(controller.unclaimed().body!!.map { it.name }).containsExactly("Anna")

        val offline: DiscordMemberDirectory = mock { on { unclaimed() } doReturn null }
        assertThat(DiscordMemberController(offline).unclaimed().statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
    }
}
