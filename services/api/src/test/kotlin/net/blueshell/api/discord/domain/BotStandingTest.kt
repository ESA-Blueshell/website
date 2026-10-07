package net.blueshell.api.discord.domain

import net.blueshell.api.testsupport.configuredDiscordSettings
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.PermissionOverride
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.RoleColors
import net.dv8tion.jda.api.entities.SelfMember
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
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
    private val mods = role("908", "Mods")
    private val bot = role("904", "Blueshell bot", managed = true)
    private val claim = role("903", "Valorant")
    private val board = role("902", "Board")
    private val kandi = role("906", "Kandi")
    private val everyone = role("324", "@everyone", public = true)

    private fun overwrite(roleId: String): PermissionOverride = mock { on { id } doReturn roleId }

    private fun channel(
        id: String,
        name: String,
        vararg overwrites: String,
    ): GuildChannel {
        // Made before the stubbing below: a mock made inside a stubbing leaves it unfinished.
        val held = overwrites.map(::overwrite)
        return mock<GuildChannel>(extraInterfaces = arrayOf(IPermissionContainer::class)) {
            on { this.id } doReturn id
            on { this.name } doReturn name
            on { type } doReturn ChannelType.TEXT
        }.also { whenever((it as IPermissionContainer).rolePermissionOverrides).thenReturn(held) }
    }

    private val lounge = channel("910", "lounge", "902")
    private val adminRoom = channel("907", "admin", "905")
    private val boardRoom = channel("909", "board-room", "902")
    private val secret = channel("911", "secret", "999")

    private fun standing(
        gateway: GatewayGuild?,
        claimRoles: String = " 903 ,",
    ): BotStanding {
        val provider: ObjectProvider<GatewayGuild> = mock { on { ifAvailable } doReturn gateway }
        return BotStanding(provider, configuredDiscordSettings(claimRoles)) { setOf("905", "906", "902") }
    }

    @Test
    fun `says what the bot may do, which kept roles sit above it, the claim bot's and the kept channels it cannot change`() {
        val self: SelfMember =
            mock {
                on { hasPermission(Permission.MANAGE_ROLES) } doReturn true
                on { hasPermission(Permission.MANAGE_CHANNEL) } doReturn false
                on { hasPermission(Permission.VIEW_CHANNEL) } doReturn true
                on { hasPermission(lounge, Permission.VIEW_CHANNEL) } doReturn true
                on { hasPermission(lounge, Permission.MANAGE_PERMISSIONS) } doReturn true
                on { hasPermission(adminRoom, Permission.VIEW_CHANNEL) } doReturn false
                on { hasPermission(boardRoom, Permission.VIEW_CHANNEL) } doReturn true
                on { hasPermission(boardRoom, Permission.MANAGE_PERMISSIONS) } doReturn false
                on { hasPermission(secret, Permission.VIEW_CHANNEL) } doReturn false
                // The bot also holds a role of the people's, which is neither its own nor above it.
                on { roles } doReturn listOf(kandi, bot)
                on { canInteract(admin) } doReturn false
                on { canInteract(mods) } doReturn false
                on { canInteract(kandi) } doReturn false
                on { canInteract(board) } doReturn true
            }
        val server: Guild =
            mock {
                on { selfMember } doReturn self
                on { roles } doReturn listOf(admin, mods, kandi, bot, claim, board, everyone)
                on { channels } doReturn listOf(lounge, adminRoom, boardRoom, secret)
                on { id } doReturn "324"
            }

        val read = standing({ server }).read()

        // Mods sits above the bot too, and the bot cannot see #secret, but the site keeps neither.
        assertThat(read.copy(permissions = emptyList())).isEqualTo(
            BotStandingResult(
                connected = true,
                manageRoles = true,
                manageChannels = false,
                botRole = DiscordRole("904", "Blueshell bot", null),
                above = listOf(DiscordRole("905", "Admin", null)),
                claimed = listOf(DiscordRole("903", "Valorant", null)),
                hidden =
                    listOf(
                        BotHiddenChannel("907", "324", "admin", null, false, BotChannelProblem.CANNOT_SEE),
                        BotHiddenChannel("909", "324", "board-room", null, false, BotChannelProblem.CANNOT_CHANGE_ACCESS),
                    ),
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
