package net.blueshell.api.discord.domain

import net.blueshell.api.discord.api.DiscordChannelKeeper
import net.blueshell.api.discord.api.DiscordUnavailable
import net.blueshell.api.discord.api.KeptChannel
import net.blueshell.api.discord.api.KeptChannelKind
import net.blueshell.api.discord.persistence.RoleAccess
import net.blueshell.api.discord.persistence.RoleOpening
import net.blueshell.api.discord.persistence.RoleOpeningKey
import net.blueshell.api.discord.persistence.RoleOpeningRepository
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.PermissionOverride
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.Category
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.api.requests.restaction.PermissionOverrideAction
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider
import org.springframework.web.server.ResponseStatusException
import java.util.EnumSet
import java.util.Optional

class RoleOpeningsTest {
    private val member: Role = mock()

    private fun override(vararg allowed: Permission): PermissionOverride =
        mock { on { this.allowed } doReturn EnumSet.noneOf(Permission::class.java).apply { addAll(allowed) } }

    private val games: Category =
        mock {
            on { id } doReturn "10"
            on { name } doReturn "Games"
            on { type } doReturn ChannelType.CATEGORY
        }
    private val lounge: TextChannel =
        mock {
            on { id } doReturn "1"
            on { name } doReturn "members-lounge"
            on { type } doReturn ChannelType.TEXT
        }
    private val news: TextChannel =
        mock {
            on { id } doReturn "2"
            on { name } doReturn "announcements"
            on { type } doReturn ChannelType.TEXT
        }
    private val voice: VoiceChannel =
        mock {
            on { id } doReturn "3"
            on { name } doReturn "Lounge"
            on { type } doReturn ChannelType.VOICE
        }
    private val quiet: TextChannel =
        mock {
            on { id } doReturn "4"
            on { name } doReturn "quiet"
            on { type } doReturn ChannelType.TEXT
        }
    private val thread: ThreadChannel = mock { on { type } doReturn ChannelType.GUILD_PUBLIC_THREAD }
    private val guild: Guild =
        mock {
            on { channels } doReturn listOf(games, lounge, news, voice, quiet, thread)
            on { getRoleById("500") } doReturn member
            on { getGuildChannelById("1") } doReturn lounge
        }
    private val kept: RoleOpeningRepository = mock { on { save(any<RoleOpening>()) } doAnswer { it.arguments[0] as RoleOpening } }
    private val keeper: DiscordChannelKeeper = mock()

    private fun openings(gateway: GatewayGuild? = GatewayGuild { guild }): RoleOpenings {
        val provider: ObjectProvider<GatewayGuild> = mock { on { ifAvailable } doReturn gateway }
        return RoleOpenings(provider, kept, keeper)
    }

    private fun opening(
        channelId: String,
        access: RoleAccess,
    ) = RoleOpening(RoleOpeningKey("500", channelId), access)

    @Test
    fun `reads each channel and category the role opens at the access kept and the access Discord has`() {
        val writes = override(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND)
        val reads = override(Permission.VIEW_CHANNEL)
        val speaks = override(Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT, Permission.VOICE_SPEAK)
        val hidden = override(Permission.MESSAGE_HISTORY)
        whenever(games.getPermissionOverride(member)).thenReturn(writes)
        whenever(lounge.getPermissionOverride(member)).thenReturn(reads)
        whenever(voice.getPermissionOverride(member)).thenReturn(speaks)
        whenever(quiet.getPermissionOverride(member)).thenReturn(hidden)
        whenever(
            kept.findAllByKeyRoleId("500"),
        ).thenReturn(listOf(opening("10", RoleAccess.WRITE), opening("1", RoleAccess.WRITE), opening("2", RoleAccess.READ)))

        val read = openings().read("500")

        assertThat(read).containsExactly(
            RoleOpeningState(KeptChannel("10", "Games", KeptChannelKind.CATEGORY, null), RoleAccess.WRITE, RoleAccess.WRITE),
            RoleOpeningState(KeptChannel("1", "members-lounge", KeptChannelKind.TEXT, null), RoleAccess.WRITE, RoleAccess.READ),
            RoleOpeningState(KeptChannel("2", "announcements", KeptChannelKind.TEXT, null), RoleAccess.READ, null),
            RoleOpeningState(KeptChannel("3", "Lounge", KeptChannelKind.VOICE, null), null, RoleAccess.SPEAK),
        )
        assertThat(read.map { it.differs }).containsExactly(false, true, true, true)
    }

    @Test
    fun `writes an access as the role's overwrite and keeps it, and takes it away again`() {
        val writing: PermissionOverrideAction =
            mock {
                on { setAllowed(any<Collection<Permission>>()) } doReturn it
                on { setDenied(any<Collection<Permission>>()) } doReturn it
            }
        whenever(lounge.upsertPermissionOverride(member)).thenReturn(writing)
        whenever(kept.findById(RoleOpeningKey("500", "1"))).thenReturn(Optional.empty(), Optional.of(opening("1", RoleAccess.WRITE)))

        openings().set("500", "1", RoleAccess.READ)
        openings().set("500", "1", RoleAccess.SPEAK)
        val written = openings().set("500", "1", RoleAccess.WRITE)

        verify(writing).setAllowed(listOf(Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY))
        verify(writing).setDenied(listOf(Permission.MESSAGE_SEND))
        verify(writing).setAllowed(listOf(Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT, Permission.VOICE_SPEAK))
        verify(writing).setAllowed(listOf(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY))
        val saved = argumentCaptor<RoleOpening>()
        verify(kept).save(saved.capture())
        assertThat(saved.firstValue.access).isEqualTo(RoleAccess.READ)
        assertThat(saved.firstValue.id).isEqualTo(RoleOpeningKey("500", "1"))
        assertThat(written).isEmpty()

        val deleting: AuditableRestAction<Void> = mock()
        val own = override(Permission.VIEW_CHANNEL)
        whenever(own.delete()).thenReturn(deleting)
        whenever(lounge.getPermissionOverride(member)).thenReturn(own, own, null)
        openings().remove("500", "1")
        openings().remove("500", "1")
        verify(deleting).complete()
        verify(kept, org.mockito.kotlin.times(2)).deleteById(RoleOpeningKey("500", "1"))
    }

    @Test
    fun `makes a private channel for the role under a category, and archives one`() {
        whenever(keeper.createPrivate("lounge", "Members", "500")).thenReturn(KeptChannel("1", "lounge", KeptChannelKind.TEXT, "Members"))
        val writing: PermissionOverrideAction =
            mock {
                on { setAllowed(any<Collection<Permission>>()) } doReturn it
                on { setDenied(any<Collection<Permission>>()) } doReturn it
            }
        whenever(lounge.upsertPermissionOverride(member)).thenReturn(writing)
        whenever(kept.findById(RoleOpeningKey("500", "1"))).thenReturn(Optional.empty())

        openings().create("500", " lounge ", "Members", RoleAccess.WRITE)
        openings().archive("500", "1")

        verify(keeper).archive(listOf("1"))
        whenever(keeper.archive(listOf("2"))).thenThrow(DiscordUnavailable("gone"))
        assertThatThrownBy { openings().archive("500", "2") }.isInstanceOf(DiscordUnreachable::class.java)
    }

    @Test
    fun `refuses without a bot, an unknown role or an unknown channel`() {
        assertThatThrownBy { openings(gateway = null).read("500") }.isInstanceOf(DiscordUnreachable::class.java)
        assertThatThrownBy { openings().read("999") }.isInstanceOf(ResponseStatusException::class.java)
        assertThatThrownBy { openings().set("500", "999", RoleAccess.READ) }.isInstanceOf(ResponseStatusException::class.java)
        assertThat(RoleOpening::class.java.getDeclaredConstructor().newInstance()).isNotNull
    }
}
