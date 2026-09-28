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
import org.springframework.beans.factory.ObjectProvider

class DiscordRoleDirectoryTest {
    private fun role(
        id: String,
        name: String,
        position: Int,
        managed: Boolean = false,
        colour: Int = 0,
    ): GuildRoleResponse =
        mock {
            on { this.id } doReturn id
            on { this.name } doReturn name
            on { this.position } doReturn position
            on { this.managed } doReturn managed
            on { color } doReturn colour
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
    fun `keeps the list rather than asking Discord on every read`() {
        val roles = listOf(role("901", "Gamers", 1))
        val api: DiscordApi = mock { on { listGuildRoles("324") } doReturn roles }
        val directory = directory(api)

        directory.pingable()
        assertThat(directory.named(setOf("901"))).containsExactly(DiscordRoleName("901", "Gamers", null))
        verify(api, times(1)).listGuildRoles("324")
    }

    @Test
    fun `lists nothing without a bot, or where Discord never answered`() {
        val failing: DiscordApi = mock { on { listGuildRoles("324") } doThrow IllegalStateException("down") }

        assertThat(directory(null).pingable()).isNull()
        assertThat(directory(failing).pingable()).isNull()
    }

    @Test
    fun `names any role a description mentions, with its colour where it has one`() {
        val roles = listOf(role("324", "@everyone", 0), role("901", "Gamers", 1, colour = 0x3498DB), role("903", "Bot", 6, managed = true))
        val api: DiscordApi = mock { on { listGuildRoles("324") } doReturn roles }

        assertThat(directory(api).named(setOf("901", "903", "999")))
            .containsExactly(DiscordRoleName("901", "Gamers", 0x3498DB), DiscordRoleName("903", "Bot", null))
        assertThat(directory(null).named(setOf("901"))).isNull()
    }
}
