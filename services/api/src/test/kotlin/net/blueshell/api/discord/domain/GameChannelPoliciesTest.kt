package net.blueshell.api.discord.domain

import net.blueshell.api.discord.persistence.ChannelAccess
import net.blueshell.api.discord.persistence.ChannelPolicy
import net.blueshell.api.discord.persistence.ChannelPolicyRepository
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.concrete.Category
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.requests.restaction.ChannelAction
import net.dv8tion.jda.api.requests.restaction.PermissionOverrideAction
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider
import org.springframework.web.server.ResponseStatusException
import java.util.Optional

class GameChannelPoliciesTest {
    private val everyone: Role = mock()
    private val member: Role = mock()
    private val overriding: PermissionOverrideAction =
        mock {
            on { setAllowed(any<Collection<Permission>>()) } doReturn it
            on { setDenied(any<Collection<Permission>>()) } doReturn it
        }
    private val channel: TextChannel =
        mock {
            on { id } doReturn "1"
            on { name } doReturn "bs-valo"
            on { upsertPermissionOverride(any()) } doReturn overriding
        }
    private val games: Category = mock()
    private val guild: Guild =
        mock {
            on { id } doReturn "99"
            on { publicRole } doReturn everyone
            on { getRoleById("500") } doReturn member
            on { getGuildChannelById("1") } doReturn channel
        }
    private val repository: ChannelPolicyRepository = mock()

    private fun policies(
        gateway: GatewayGuild? = GatewayGuild { guild },
        memberRole: String = "500",
    ): GameChannelPolicies {
        val provider: ObjectProvider<GatewayGuild> = mock { on { ifAvailable } doReturn gateway }
        return GameChannelPolicies(provider, repository, "Games", "Esports", memberRole)
    }

    private fun allows(
        role: Role,
        view: Boolean,
        send: Boolean,
    ) {
        whenever(role.hasPermission(channel, Permission.VIEW_CHANNEL)).thenReturn(view)
        whenever(role.hasPermission(channel, Permission.MESSAGE_SEND)).thenReturn(send)
    }

    @Test
    fun `makes a channel under the games or esports category with everybody reading and members writing`() {
        whenever(guild.getCategoriesByName("Esports", true)).thenReturn(emptyList())
        val makingCategory: ChannelAction<Category> = mock { on { complete() } doReturn games }
        whenever(guild.createCategory("Esports")).thenReturn(makingCategory)
        whenever(guild.getCategoriesByName("Games", true)).thenReturn(listOf(games))
        val making: ChannelAction<TextChannel> = mock { on { complete() } doReturn channel }
        whenever(games.createTextChannel("bs-valo")).thenReturn(making)

        assertThat(policies().create(" bs-valo ", GameChannelCategory.GAMES)).isEqualTo(MadeChannel("1", "99", "bs-valo"))
        policies().create("bs-valo", GameChannelCategory.ESPORTS)

        verify(overriding, times(2)).setAllowed(listOf(Permission.VIEW_CHANNEL))
        verify(overriding, times(2)).setDenied(listOf(Permission.MESSAGE_SEND))
        val kept = argumentCaptor<ChannelPolicy>()
        verify(repository, times(2)).save(kept.capture())
        assertThat(listOf(kept.firstValue.everyone, kept.firstValue.members)).containsExactly(ChannelAccess.READ, ChannelAccess.WRITE)
        assertThat(kept.firstValue.id).isEqualTo("1")
        assertThat(ChannelPolicy::class.java.getDeclaredConstructor().newInstance()).isNotNull
    }

    @Test
    fun `says where Discord differs from what the site keeps, and nothing differs where nothing is kept`() {
        allows(everyone, view = true, send = true)
        allows(member, view = true, send = true)
        whenever(repository.findById("1")).thenReturn(Optional.of(ChannelPolicy("1", ChannelAccess.READ, ChannelAccess.WRITE)))

        val read = policies().read("1")
        assertThat(read.actual).isEqualTo(AccessPolicy(ChannelAccess.WRITE, ChannelAccess.WRITE))
        assertThat(read.differs).isTrue()

        allows(everyone, view = false, send = false)
        allows(member, view = true, send = false)
        whenever(repository.findById("1")).thenReturn(Optional.empty())
        assertThat(policies().read("1")).isEqualTo(ChannelAccessState(null, AccessPolicy(ChannelAccess.HIDDEN, ChannelAccess.READ)))
        assertThat(policies().read("1").differs).isFalse()
        assertThat(policies(memberRole = "").read("1").actual).isEqualTo(AccessPolicy(ChannelAccess.HIDDEN, ChannelAccess.HIDDEN))
    }

    @Test
    fun `writes a changed policy to Discord and keeps it, a hidden channel denying the view`() {
        allows(everyone, view = false, send = false)
        allows(member, view = true, send = true)
        whenever(
            repository.findById("1"),
        ).thenReturn(Optional.empty(), Optional.of(ChannelPolicy("1", ChannelAccess.READ, ChannelAccess.READ)))

        policies().set("1", AccessPolicy(ChannelAccess.HIDDEN, ChannelAccess.WRITE))
        verify(overriding).setDenied(listOf(Permission.VIEW_CHANNEL))
        verify(overriding).setAllowed(listOf(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND))

        val kept = ChannelPolicy("1", ChannelAccess.READ, ChannelAccess.READ)
        whenever(repository.findById("1")).thenReturn(Optional.of(kept))
        policies().set("1", AccessPolicy(ChannelAccess.WRITE, ChannelAccess.WRITE))
        assertThat(listOf(kept.everyone, kept.members)).containsExactly(ChannelAccess.WRITE, ChannelAccess.WRITE)
    }

    @Test
    fun `refuses without a bot, and for a channel Discord does not have`() {
        assertThatThrownBy { policies(gateway = null).read("1") }.isInstanceOf(DiscordUnreachable::class.java)
        assertThatThrownBy { policies().read("2") }.isInstanceOf(ResponseStatusException::class.java)
    }
}
