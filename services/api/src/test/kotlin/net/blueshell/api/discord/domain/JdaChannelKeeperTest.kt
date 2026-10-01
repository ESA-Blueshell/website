package net.blueshell.api.discord.domain

import net.blueshell.api.discord.api.DiscordUnavailable
import net.blueshell.api.discord.api.KeptChannel
import net.blueshell.api.discord.api.KeptChannelKind
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
import net.dv8tion.jda.api.requests.restaction.ChannelAction
import net.dv8tion.jda.api.requests.restaction.PermissionOverrideAction
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider
import java.util.EnumSet

class JdaChannelKeeperTest {
    private val archived: net.blueshell.api.discord.persistence.ArchivedChannelRepository = mock()
    private val everyone: Role = mock()
    private val sitecieRole: Role = mock { on { id } doReturn "900" }
    private val committees: Category =
        mock {
            on { id } doReturn "10"
            on { name } doReturn "Committees"
            on { type } doReturn ChannelType.CATEGORY
        }
    private val opened: PermissionOverride = mock { on { allowed } doReturn EnumSet.of(Permission.VIEW_CHANNEL) }
    private val text: TextChannel =
        mock {
            on { id } doReturn "1"
            on { name } doReturn "sitecie"
            on { type } doReturn ChannelType.TEXT
            on { parentCategory } doReturn committees
            on { getPermissionOverride(sitecieRole) } doReturn opened
        }
    private val voice: VoiceChannel =
        mock {
            on { id } doReturn "2"
            on { name } doReturn "sitecie-voice"
            on { type } doReturn ChannelType.VOICE
        }
    private val thread: ThreadChannel = mock { on { type } doReturn ChannelType.GUILD_PUBLIC_THREAD }
    private val guild: Guild =
        mock {
            on { channels } doReturn listOf(committees, text, voice, thread)
            on { getRoleById("900") } doReturn sitecieRole
            on { publicRole } doReturn everyone
            on { getGuildChannelById("1") } doReturn text
            on { getGuildChannelById("2") } doReturn voice
        }

    private fun keeper(gateway: GatewayGuild? = GatewayGuild { guild }): JdaChannelKeeper {
        val provider: ObjectProvider<GatewayGuild> = mock { on { ifAvailable } doReturn gateway }
        return JdaChannelKeeper(provider, archived, "Archive")
    }

    @Test
    fun `lists channels and categories, and the ones a role is let into`() {
        assertThat(keeper().available()).isTrue()
        assertThat(keeper().channels()).containsExactly(
            KeptChannel("10", "Committees", KeptChannelKind.CATEGORY, null),
            KeptChannel("1", "sitecie", KeptChannelKind.TEXT, "Committees"),
            KeptChannel("2", "sitecie-voice", KeptChannelKind.VOICE, null),
        )
        assertThat(keeper().openedTo("900")).containsExactly(KeptChannel("1", "sitecie", KeptChannelKind.TEXT, "Committees"))
    }

    @Test
    fun `makes a private channel under the category, made where it is missing`() {
        whenever(guild.getCategoriesByName("Committees", true)).thenReturn(emptyList())
        val makingCategory: ChannelAction<Category> = mock { on { complete() } doReturn committees }
        whenever(guild.createCategory("Committees")).thenReturn(makingCategory)
        val making: ChannelAction<TextChannel> = mock()
        whenever(committees.createTextChannel("sitecie")).thenReturn(making)
        whenever(
            making.addPermissionOverride(eq(everyone), any<Collection<Permission>>(), any<Collection<Permission>>()),
        ).thenReturn(making)
        whenever(
            making.addPermissionOverride(eq(sitecieRole), any<Collection<Permission>>(), any<Collection<Permission>>()),
        ).thenReturn(making)
        whenever(making.complete()).thenReturn(text)

        assertThat(keeper().createPrivate("sitecie", "Committees", "900").id).isEqualTo("1")
        verify(making).addPermissionOverride(everyone, emptyList(), listOf(Permission.VIEW_CHANNEL))
    }

    @Test
    fun `opens a channel to a role, keeping everybody else out where private, and closes it again`() {
        val granting: PermissionOverrideAction = mock()
        whenever(granting.grant(anyVararg<Permission>())).thenReturn(granting)
        whenever(granting.grant(any<Collection<Permission>>())).thenReturn(granting)
        val denying: PermissionOverrideAction = mock()
        whenever(denying.deny(anyVararg<Permission>())).thenReturn(denying)
        whenever(text.upsertPermissionOverride(sitecieRole)).thenReturn(granting)
        whenever(text.upsertPermissionOverride(everyone)).thenReturn(denying)
        whenever(voice.upsertPermissionOverride(sitecieRole)).thenReturn(granting)
        val deleting: AuditableRestAction<Void> = mock()
        whenever(opened.delete()).thenReturn(deleting)

        keeper().open("1", "900", private = true)
        keeper().open("2", "900", private = false)
        keeper().close("1", "900")
        val removing: AuditableRestAction<Void> = mock()
        whenever(text.delete()).thenReturn(removing)
        keeper().delete("1")
        keeper().delete("999")
        verify(removing).complete()

        verify(denying).complete()
        verify(deleting).complete()
    }

    @Test
    fun `without a bot, a role or a channel it refuses`() {
        assertThat(keeper(gateway = null).available()).isFalse()
        assertThatThrownBy { keeper(gateway = GatewayGuild { null }).channels() }.isInstanceOf(DiscordUnavailable::class.java)
        assertThatThrownBy { keeper().openedTo("999") }.isInstanceOf(DiscordUnavailable::class.java)
        assertThatThrownBy { keeper().close("999", "900") }.isInstanceOf(DiscordUnavailable::class.java)
    }

    @Test
    fun `archives a channel into the archive category remembering where it was, and restores it there`() {
        val archive: Category = mock { on { idLong } doReturn 20 }
        whenever(guild.getCategoriesByName("Archive", true)).thenReturn(emptyList(), listOf(archive))
        val makingArchive: ChannelAction<Category> = mock { on { complete() } doReturn archive }
        whenever(guild.createCategory("Archive")).thenReturn(makingArchive)
        val manager: net.dv8tion.jda.api.managers.channel.concrete.TextChannelManager = mock()
        whenever(text.manager).thenReturn(manager)
        whenever(manager.setParent(any())).thenReturn(manager)
        whenever(text.parentCategoryId).thenReturn("10")
        whenever(text.parentCategoryIdLong).thenReturn(10, 20)
        whenever(guild.getCategoryById("10")).thenReturn(committees)

        keeper().archive(listOf("1", "999"))
        keeper().archive(listOf("1"))
        verify(archived).save(net.blueshell.api.discord.persistence.ArchivedChannel("1", "10").let { org.mockito.kotlin.argThat { channelId == "1" && categoryId == "10" } })
        verify(manager).setParent(archive)

        whenever(archived.findById("1")).thenReturn(java.util.Optional.of(net.blueshell.api.discord.persistence.ArchivedChannel("1", "10")))
        whenever(archived.findById("2")).thenReturn(java.util.Optional.empty())
        keeper().restore(listOf("1", "2"))
        verify(manager).setParent(committees)
        verify(archived).delete(org.mockito.kotlin.argThat { channelId == "1" })
        assertThat(net.blueshell.api.discord.persistence.ArchivedChannel::class.java.getDeclaredConstructor().newInstance()).isNotNull
        assertThat(net.blueshell.api.discord.persistence.ArchivedChannel("1", null).id).isEqualTo("1")
    }
}
