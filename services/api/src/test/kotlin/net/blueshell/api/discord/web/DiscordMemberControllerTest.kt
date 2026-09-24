package net.blueshell.api.discord.web

import net.blueshell.api.discord.domain.DiscordMember
import net.blueshell.api.discord.domain.DiscordGameChannels
import net.blueshell.api.discord.domain.DiscordMemberDirectory
import net.blueshell.api.discord.domain.TextRoom
import net.blueshell.api.discord.domain.DiscordRole
import net.blueshell.api.discord.domain.DiscordRoleDirectory
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
    private val roles: DiscordRoleDirectory = mock { on { pingable() } doReturn listOf(DiscordRole("901", "Gamers")) }
    private val channels: DiscordGameChannels =
        mock { on { offered() } doReturn listOf(TextRoom("11", "324", "valorant", "Games")) }
    private val controller = DiscordMemberController(directory, roles, channels)

    @Test
    fun `answers the members found`() {
        val answer = controller.search("nel")

        assertThat(answer.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(answer.body).containsExactly(DiscordMemberResponse("803", "Nelly B", "nelly", "https://cdn/nelly.png"))
        val nelly = answer.body!!.single()
        assertThat(listOf(nelly.id, nelly.username, nelly.avatar)).containsExactly("803", "nelly", "https://cdn/nelly.png")
    }

    @Test
    fun `answers 503 without a bot, so the picker falls back to typing`() {
        assertThat(controller.search("off").statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
    }

    @Test
    fun `answers everybody nobody has linked yet, or 503 without a bot`() {
        assertThat(controller.unclaimed().body!!.map { it.name }).containsExactly("Anna")

        val offline: DiscordMemberDirectory = mock { on { unclaimed() } doReturn null }
        assertThat(DiscordMemberController(offline, roles, channels).unclaimed().statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
    }

    @Test
    fun `answers the roles an event may ping, or 503 without a bot`() {
        assertThat(controller.roles().body).containsExactly(DiscordRoleResponse("901", "Gamers"))
        assertThat(controller.roles().body!!.single().let { it.id to it.name }).isEqualTo("901" to "Gamers")

        val offline: DiscordRoleDirectory = mock { on { pingable() } doReturn null }
        assertThat(DiscordMemberController(directory, offline, channels).roles().statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
    }

    @Test
    fun `answers the channels a game may live in, or 503 without a bot`() {
        assertThat(controller.channels().body).containsExactly(DiscordChannelResponse("11", "324", "valorant"))
        val offline: DiscordGameChannels = mock { on { offered() } doReturn null }
        assertThat(DiscordMemberController(directory, roles, offline).channels().statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
    }
}
