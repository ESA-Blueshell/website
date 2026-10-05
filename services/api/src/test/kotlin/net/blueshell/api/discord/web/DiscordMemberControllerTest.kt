package net.blueshell.api.discord.web

import net.blueshell.api.discord.domain.DiscordEmoji
import net.blueshell.api.discord.domain.DiscordEmojiDirectory
import net.blueshell.api.discord.domain.DiscordGameChannels
import net.blueshell.api.discord.domain.DiscordMember
import net.blueshell.api.discord.domain.DiscordMemberDirectory
import net.blueshell.api.discord.domain.DiscordRole
import net.blueshell.api.discord.domain.DiscordRoleDirectory
import net.blueshell.api.discord.domain.GameChannelCategory
import net.blueshell.api.discord.domain.TextRoom
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
    private val roles: DiscordRoleDirectory =
        mock {
            on { pingable() } doReturn listOf(DiscordRole("901", "Gamers", 0x3498DB), DiscordRole("902", "Board", null))
        }
    private val channels: DiscordGameChannels =
        mock {
            on { offered(GameChannelCategory.GAMES) } doReturn listOf(TextRoom("11", "324", "valorant", "Games"))
            on { offered(GameChannelCategory.ESPORTS) } doReturn listOf(TextRoom("12", "324", "valorant-esports", "Esports"))
            on { everywhere() } doReturn listOf(TextRoom("13", "324", "bs-valorant", "Valorant"), TextRoom("14", "324", "rules"))
        }
    private val emoji: DiscordEmojiDirectory = mock { on { all() } doReturn listOf(DiscordEmoji("657", "ShellyStar", true)) }
    private val controller = DiscordMemberController(directory, roles, channels, emoji)

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
        assertThat(DiscordMemberController(offline, roles, channels, emoji).unclaimed().statusCode)
            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
    }

    @Test
    fun `answers the roles an event may ping with their colours, or 503 without a bot`() {
        assertThat(controller.roles().body)
            .containsExactly(DiscordRoleResponse("901", "Gamers", 0x3498DB), DiscordRoleResponse("902", "Board", null))
        assertThat(controller.roles().body!!.map { it.colour }).containsExactly(0x3498DB, null)

        val offline: DiscordRoleDirectory = mock { on { pingable() } doReturn null }
        assertThat(DiscordMemberController(directory, offline, channels, emoji).roles().statusCode)
            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
    }

    @Test
    fun `answers the channels a game may live in, or 503 without a bot`() {
        assertThat(controller.channels(GameChannelCategory.GAMES).body).containsExactly(DiscordChannelResponse("11", "324", "valorant", "Games"))
        assertThat(
            controller
                .channels(GameChannelCategory.GAMES)
                .body!!
                .single()
                .guildId,
        ).isEqualTo("324")
        assertThat(controller.channels(GameChannelCategory.ESPORTS).body!!.map { it.name }).containsExactly("valorant-esports")
        assertThat(controller.channels(GameChannelCategory.ESPORTS, everywhere = true).body!!.map { it.name to it.category })
            .containsExactly("bs-valorant" to "Valorant", "rules" to null)
        val offline: DiscordGameChannels = mock { on { offered(GameChannelCategory.GAMES) } doReturn null }
        assertThat(DiscordMemberController(directory, roles, offline, emoji).channels(GameChannelCategory.GAMES).statusCode)
            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
    }

    @Test
    fun `answers the server's own emoji, or 503 without a bot`() {
        val star = controller.emojis().body!!.single()
        assertThat(listOf(star.id, star.name, star.animated)).containsExactly("657", "ShellyStar", true)

        val offline: DiscordEmojiDirectory = mock { on { all() } doReturn null }
        assertThat(DiscordMemberController(directory, roles, channels, offline).emojis().statusCode)
            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
    }
}
