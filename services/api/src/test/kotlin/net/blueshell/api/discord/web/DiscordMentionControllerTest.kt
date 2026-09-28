package net.blueshell.api.discord.web

import net.blueshell.api.discord.domain.DiscordChannel
import net.blueshell.api.discord.domain.DiscordChannelDirectory
import net.blueshell.api.discord.domain.DiscordMember
import net.blueshell.api.discord.domain.DiscordMemberDirectory
import net.blueshell.api.discord.domain.DiscordMentionNameSource
import net.blueshell.api.discord.domain.DiscordRoleDirectory
import net.blueshell.api.discord.domain.DiscordRoleName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.http.HttpStatus

class DiscordMentionControllerTest {
    private val members: DiscordMemberDirectory =
        mock { on { named(any()) } doReturn listOf(DiscordMember("11", "Anna", "anna", "https://cdn/anna.png")) }
    private val roles: DiscordRoleDirectory = mock { on { named(any()) } doReturn listOf(DiscordRoleName("901", "Gamers", 0x3498DB)) }
    private val channels: DiscordChannelDirectory =
        mock { on { open() } doReturn listOf(DiscordChannel("1", "general"), DiscordChannel("2", "events-info")) }
    private val controller = controller(members, roles, channels)

    private fun controller(
        members: DiscordMemberDirectory,
        roles: DiscordRoleDirectory,
        channels: DiscordChannelDirectory,
    ) = DiscordMentionController(DiscordMentionNameSource(members, roles, channels), channels)

    @Test
    fun `names what a description mentions`() {
        val named = controller.mentions(listOf("11", " "), listOf("901"), listOf("2", "99")).body!!

        assertThat(named.users).containsExactly(DiscordNameResponse("11", "Anna"))
        assertThat(named.roles).containsExactly(DiscordRoleNameResponse("901", "Gamers", 0x3498DB))
        assertThat(named.channels.map { it.id to it.name }).containsExactly("2" to "events-info")
        assertThat(named.roles.single().colour).isEqualTo(0x3498DB)
    }

    @Test
    fun `asks Discord for nothing a description does not mention`() {
        val quiet: DiscordMemberDirectory = mock()
        val none: DiscordRoleDirectory = mock()
        val shut: DiscordChannelDirectory = mock()

        val named = controller(quiet, none, shut).mentions(emptyList(), emptyList(), emptyList()).body!!

        assertThat(named.users + named.channels).isEmpty()
        assertThat(named.roles).isEmpty()
        verifyNoInteractions(quiet, none, shut)
    }

    @Test
    fun `answers 503 where any of them cannot be asked`() {
        val offline: DiscordChannelDirectory = mock { on { open() } doReturn null }

        val answer = controller(members, roles, offline).mentions(listOf("11"), emptyList(), listOf("1"))

        assertThat(answer.statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
    }

    @Test
    fun `lists the channels a description may mention, or 503 without a bot`() {
        assertThat(controller.channels().body!!.map { it.name }).containsExactly("general", "events-info")

        val offline: DiscordChannelDirectory = mock { on { open() } doReturn null }
        assertThat(controller(members, roles, offline).channels().statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
    }
}
