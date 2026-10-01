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
        return JdaChannelKeeper(provider)
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
}
