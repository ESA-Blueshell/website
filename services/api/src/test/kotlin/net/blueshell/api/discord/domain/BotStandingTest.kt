package net.blueshell.api.discord.domain

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.RoleColors
import net.dv8tion.jda.api.entities.SelfMember
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.beans.factory.ObjectProvider

class BotStandingTest {
    private fun role(
        id: String,
        name: String,
        public: Boolean = false,
        managed: Boolean = false,
    ): Role =
        mock {
            on { this.id } doReturn id
            on { this.name } doReturn name
            on { isPublicRole } doReturn public
            on { isManaged } doReturn managed
            on { colors } doReturn RoleColors.DEFAULT
        }

    private val admin = role("905", "Admin")
    private val bot = role("904", "Blueshell bot", managed = true)
    private val claim = role("903", "Valorant")
    private val board = role("902", "Board")
    private val everyone = role("324", "@everyone", public = true)
    private val lounge: GuildChannel = mock { on { name } doReturn "lounge" }
    private val mods: GuildChannel = mock { on { name } doReturn "mods" }

    private fun standing(
        gateway: GatewayGuild?,
        claimRoles: String = " 903 ,",
    ): BotStanding {
        val provider: ObjectProvider<GatewayGuild> = mock { on { ifAvailable } doReturn gateway }
        return BotStanding(provider, claimRoles)
    }

    @Test
    fun `says what the bot may do, which roles sit above it and which the claim bot hands out`() {
        val self: SelfMember =
            mock {
                on { hasPermission(Permission.MANAGE_ROLES) } doReturn true
                on { hasPermission(Permission.MANAGE_CHANNEL) } doReturn false
                on { hasPermission(Permission.VIEW_CHANNEL) } doReturn true
                on { hasPermission(lounge, Permission.VIEW_CHANNEL) } doReturn true
                on { hasPermission(mods, Permission.VIEW_CHANNEL) } doReturn false
                on { roles } doReturn listOf(bot)
                on { canInteract(admin) } doReturn false
                on { canInteract(board) } doReturn true
            }
        val server: Guild =
            mock {
                on { selfMember } doReturn self
                on { roles } doReturn listOf(admin, bot, claim, board, everyone)
                on { channels } doReturn listOf(lounge, mods)
            }

        val read = standing({ server }).read()

        assertThat(read.copy(permissions = emptyList())).isEqualTo(
            BotStandingResult(
                connected = true,
                manageRoles = true,
                manageChannels = false,
                botRole = DiscordRole("904", "Blueshell bot", null),
                above = listOf(DiscordRole("905", "Admin", null)),
                claimed = listOf(DiscordRole("903", "Valorant", null)),
                hidden = listOf("mods"),
            ),
        )
        // Each permission is said by Discord's own name for it, with what the site needs it for.
        assertThat(read.permissions.filter { it.granted }.map { it.name }).containsExactly("View Channels", "Manage Roles")
        assertThat(read.permissions.filterNot { it.granted }.map { it.name })
            .containsExactly(
                "Manage Channels",
                "Create Invite",
                "Send Messages",
                "Mention @everyone, @here, and All Roles",
                "Read Message History",
            )
        assertThat(read.permissions.single { it.name == "Manage Channels" }.neededFor)
            .isEqualTo("Make a channel, archive it and remove it")
    }

    @Test
    fun `without a bot or before the gateway has the server it says only that it is not connected`() {
        val absent = BotStandingResult(false, false, false, null, emptyList(), emptyList())
        assertThat(standing(null).read()).isEqualTo(absent)
        assertThat(standing({ null }, "").read()).isEqualTo(absent)
    }
}
