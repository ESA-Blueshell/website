package net.blueshell.api.discord.domain

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.RoleColors
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.beans.factory.ObjectProvider

class DiscordRoleDirectoryTest {
    private fun role(
        id: String,
        name: String,
        public: Boolean = false,
        managed: Boolean = false,
        colour: Int? = null,
    ): Role =
        mock {
            on { this.id } doReturn id
            on { this.name } doReturn name
            on { isPublicRole } doReturn public
            on { isManaged } doReturn managed
            on { colors } doReturn (colour?.let { RoleColors(it, it, it) } ?: RoleColors.DEFAULT)
        }

    // The gateway lists roles highest first, @everyone last.
    private fun guild(vararg roles: Role): Guild = mock { on { this.roles } doReturn roles.toList() }

    private fun directory(gateway: GatewayGuild?): DiscordRoleDirectory {
        val provider: ObjectProvider<GatewayGuild> = mock { on { ifAvailable } doReturn gateway }
        return DiscordRoleDirectory(provider)
    }

    @Test
    fun `lists the roles an event may ping in the server's order, with their colours, without @everyone or integration roles`() {
        val server =
            guild(
                role("903", "Bot", managed = true),
                role("902", "Board", colour = 0xE91E63),
                role("901", "Gamers"),
                role("324", "@everyone", public = true),
            )

        assertThat(directory { server }.pingable())
            .containsExactly(DiscordRole("902", "Board", 0xE91E63), DiscordRole("901", "Gamers", null))
    }

    @Test
    fun `names any role a description mentions, with its colour where it has one`() {
        val server =
            guild(role("903", "Bot", managed = true), role("901", "Gamers", colour = 0x3498DB), role("324", "@everyone", public = true))

        assertThat(directory { server }.named(setOf("901", "903", "999")))
            .containsExactly(DiscordRole("903", "Bot", null), DiscordRole("901", "Gamers", 0x3498DB))
    }

    @Test
    fun `keeps the last roles while the gateway is away`() {
        val server = guild(role("901", "Gamers"))
        var held: Guild? = server
        val directory = directory { held }

        assertThat(directory.pingable()).containsExactly(DiscordRole("901", "Gamers", null))
        held = null
        assertThat(directory.pingable()).containsExactly(DiscordRole("901", "Gamers", null))
    }

    @Test
    fun `lists nothing without a bot, or while the gateway has never had the server`() {
        assertThat(directory(null).pingable()).isNull()
        assertThat(directory { null }.pingable()).isNull()
        assertThat(directory(null).named(setOf("901"))).isNull()
    }
}
