package net.blueshell.api.discord.domain

import net.blueshell.api.discord.api.DiscordUnavailable
import net.blueshell.api.discord.api.KeptRole
import net.blueshell.api.discord.api.RoleHolder
import net.blueshell.clients.discord.api.DiscordApi
import net.blueshell.clients.discord.model.GuildMemberResponse
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.SelfMember
import net.dv8tion.jda.api.entities.UserSnowflake
import net.dv8tion.jda.api.managers.RoleManager
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.api.requests.restaction.RoleAction
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider

class JdaRoleKeeperTest {
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
        }

    private val admin = role("905", "Admin")
    private val bot = role("904", "Bot", managed = true)
    private val claim = role("903", "Valorant")
    private val board = role("902", "Board")
    private val everyone = role("324", "@everyone", public = true)
    private val self: SelfMember =
        mock {
            on { canInteract(admin) } doReturn false
            on { canInteract(board) } doReturn true
        }
    private val guild: Guild =
        mock {
            on { roles } doReturn listOf(admin, bot, claim, board, everyone)
            on { selfMember } doReturn self
            on { getRoleById("902") } doReturn board
            on { getRoleById("903") } doReturn claim
        }
    private val api: DiscordApi = mock()

    private fun keeper(
        gateway: GatewayGuild? = GatewayGuild { guild },
        client: DiscordApi? = api,
    ): JdaRoleKeeper {
        val gateways: ObjectProvider<GatewayGuild> = mock { on { ifAvailable } doReturn gateway }
        val apis: ObjectProvider<DiscordApi> = mock { on { ifAvailable } doReturn client }
        return JdaRoleKeeper(gateways, apis, "1", "903")
    }

    private fun member(
        id: String,
        roles: List<String>,
        nick: String? = null,
    ): GuildMemberResponse {
        val account: net.blueshell.clients.discord.model.UserResponse =
            mock {
                on { this.id } doReturn id
                on { username } doReturn "user$id"
            }
        return mock {
            on { user } doReturn account
            on { this.roles } doReturn roles.toSet()
            on { this.nick } doReturn nick
        }
    }

    @Test
    fun `lists the roles the site could keep, saying which the bot can hand out, and finds one`() {
        assertThat(keeper().available()).isTrue()
        assertThat(keeper().roles()).containsExactly(KeptRole("905", "Admin", false), KeptRole("902", "Board", true))
        assertThat(keeper().role("902")).isEqualTo(KeptRole("902", "Board", true))
        assertThat(keeper().role("903")).isNull()
        assertThat(keeper().role("999")).isNull()
    }

    @Test
    fun `reads a role's holders over every page of members`() {
        val first = (1..1000).map { member("$it", if (it == 7) listOf("902") else emptyList()) }
        whenever(api.listGuildMembers("1", 1000, null)).thenReturn(first)
        val second = listOf(member("2001", listOf("902"), nick = "Ann"))
        whenever(api.listGuildMembers("1", 1000, "1000")).thenReturn(second)

        assertThat(keeper().holders("902")).containsExactly(RoleHolder("7", "user7"), RoleHolder("2001", "Ann"))
    }

    @Test
    fun `adds, removes, makes, renames and deletes roles through the gateway`() {
        val adding: AuditableRestAction<Void> = mock()
        val removing: AuditableRestAction<Void> = mock()
        whenever(guild.addRoleToMember(any<UserSnowflake>(), eq(board))).thenReturn(adding)
        whenever(guild.removeRoleFromMember(any<UserSnowflake>(), eq(board))).thenReturn(removing)
        val making: RoleAction = mock()
        whenever(guild.createRole()).thenReturn(making)
        whenever(making.setName("Sitecie")).thenReturn(making)
        whenever(making.setMentionable(false)).thenReturn(making)
        val made = role("906", "Sitecie")
        whenever(making.complete()).thenReturn(made)
        val manager: RoleManager = mock()
        whenever(board.manager).thenReturn(manager)
        whenever(manager.setName("Bestuur")).thenReturn(manager)
        val deleting: AuditableRestAction<Void> = mock()
        whenever(board.delete()).thenReturn(deleting)

        keeper().add("902", "11")
        keeper().remove("902", "11")
        assertThat(keeper().create("Sitecie")).isEqualTo(KeptRole("906", "Sitecie", false))
        assertThat(keeper().rename("902", "Bestuur")).isEqualTo(KeptRole("902", "Bestuur", true))
        keeper().delete("902")
        keeper().delete("999")

        verify(adding).complete()
        verify(removing).complete()
        verify(manager).complete()
        verify(deleting).complete()
    }

    @Test
    fun `without a bot, a server or a role it refuses`() {
        assertThat(keeper(gateway = null).available()).isFalse()
        assertThat(keeper(client = null).available()).isFalse()
        assertThatThrownBy { keeper(gateway = GatewayGuild { null }).roles() }.isInstanceOf(DiscordUnavailable::class.java)
        assertThatThrownBy { keeper(client = null).holders("902") }.isInstanceOf(DiscordUnavailable::class.java)
        assertThatThrownBy { keeper().add("999", "11") }.isInstanceOf(DiscordUnavailable::class.java)
    }
}
