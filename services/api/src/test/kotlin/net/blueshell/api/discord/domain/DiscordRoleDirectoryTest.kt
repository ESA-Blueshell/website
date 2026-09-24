package net.blueshell.api.discord.domain

import net.blueshell.clients.discord.api.DiscordApi
import net.blueshell.clients.discord.model.GuildRoleResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DiscordRoleDirectoryTest {
    private fun role(
        id: String,
        name: String,
        position: Int,
        managed: Boolean = false,
    ): GuildRoleResponse =
        mock {
            on { this.id } doReturn id
            on { this.name } doReturn name
            on { this.position } doReturn position
            on { this.managed } doReturn managed
        }

    private fun directory(api: DiscordApi?): DiscordRoleDirectory {
        val provider: ObjectProvider<DiscordApi> = mock { on { ifAvailable } doReturn api }
        return DiscordRoleDirectory(provider, "324")
    }

    @Test
    fun `lists the roles an event may ping in the server's order, without @everyone or integration roles`() {
        val roles =
            listOf(
                role("324", "@everyone", 0),
                role("901", "Gamers", 1),
                role("902", "Board", 5),
                role("903", "Bot", 6, managed = true),
            )
        val api: DiscordApi = mock { on { listGuildRoles("324") } doReturn roles }

        assertThat(directory(api).pingable()).containsExactly(DiscordRole("902", "Board"), DiscordRole("901", "Gamers"))
    }

    @Test
    fun `keeps them five minutes, and the last list where Discord stops answering`() {
        val roles = listOf(role("901", "Gamers", 1))
        val api: DiscordApi = mock { on { listGuildRoles("324") } doReturn roles }
        val directory = directory(api)
        val start = Instant.parse("2026-09-24T10:00:00Z")
        directory.clock = Clock.fixed(start, ZoneOffset.UTC)

        directory.pingable()
        directory.pingable()
        verify(api, times(1)).listGuildRoles("324")

        directory.clock = Clock.fixed(start.plus(DiscordRoleDirectory.KEPT_FOR), ZoneOffset.UTC)
        whenever(api.listGuildRoles("324")).thenThrow(IllegalStateException("down"))
        assertThat(directory.pingable()).containsExactly(DiscordRole("901", "Gamers"))
    }

    @Test
    fun `lists nothing without a bot, or where Discord never answered`() {
        val failing: DiscordApi = mock { on { listGuildRoles("324") } doThrow IllegalStateException("down") }

        assertThat(directory(null).pingable()).isNull()
        assertThat(directory(failing).pingable()).isNull()
    }
}
