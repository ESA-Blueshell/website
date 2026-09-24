package net.blueshell.api.discord.domain

import net.blueshell.clients.discord.api.DiscordApi
import net.blueshell.clients.discord.model.PrivateApplicationResponse
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Invite
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.requests.restaction.InviteAction
import net.dv8tion.jda.api.utils.cache.CacheView
import net.dv8tion.jda.internal.entities.GuildImpl
import net.dv8tion.jda.internal.entities.MemberPresenceImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class JdaVoiceServerSourceTest {
    private val presences: CacheView.SimpleCacheView<MemberPresenceImpl> = mock { on { size() } doReturn 4L }
    private val everyone: Role = mock()
    private val guild: GuildImpl =
        mock {
            on { id } doReturn "324"
            on { name } doReturn "Blueshell"
            on { publicRole } doReturn everyone
            on { voiceChannels } doReturn emptyList()
            on { memberCount } doReturn 6
            on { presenceView } doReturn presences
        }
    private val jda: JDA = mock { on { getGuildById("324") } doReturn guild }

    private fun source(api: DiscordApi): JdaVoiceServerSource =
        JdaVoiceServerSource("token", "324", api).also { it.connect = { jda } }

    private fun applicationWith(flags: Int): DiscordApi {
        val application: PrivateApplicationResponse = mock { on { this.flags } doReturn flags }
        return mock { on { getMyOauth2Application() } doReturn application }
    }

    @Test
    fun `holds nothing before it connects`() {
        val source = source(applicationWith(0))

        assertThat(source.server()).isNull()
        assertThat(source.isRunning).isFalse()
    }

    @Test
    fun `counts from the gateway where the application has the intents`() {
        val source = source(applicationWith((1 shl 13) or (1 shl 15)))

        source.start()

        val server = source.server()!!
        assertThat(server.name).isEqualTo("Blueshell")
        assertThat(server.online to server.members).isEqualTo(4 to 6)
        assertThat(source.isRunning).isTrue()
    }

    @Test
    fun `connects without privileged intents where the flags cannot be read, and counts nothing`() {
        val api: DiscordApi = mock { on { getMyOauth2Application() } doThrow IllegalStateException("refused") }
        val source = source(api)

        source.start()

        assertThat(source.server()!!.online to source.server()!!.members).isEqualTo(null to null)
    }

    @Test
    fun `stays down where the gateway will not start`() {
        val source = JdaVoiceServerSource("token", "324", applicationWith(0))
        source.connect = { error("unreachable") }

        source.start()

        assertThat(source.isRunning).isFalse()
        assertThat(source.server()).isNull()
    }

    @Test
    fun `passes on every event to whoever listens`() {
        val source = source(applicationWith(0))
        var heard = 0
        source.onChange { heard++ }
        source.onChange { heard++ }

        source.relay.onEvent(mock<GenericEvent>())

        assertThat(heard).isEqualTo(2)
    }

    @Test
    fun `shuts the gateway down when it stops`() {
        val source = source(applicationWith(0))
        source.start()

        source.stop()

        verify(jda).shutdown()
        assertThat(source.isRunning).isFalse()
    }

    @Test
    fun `builds the connection with the granted intents, unconnected`() {
        val listener = EventListener {}

        assertThat(gatewayOf("token", setOf(GatewayIntent.GUILD_PRESENCES), listener)).isInstanceOf(JDABuilder::class.java)
        assertThat(gatewayOf("token", emptySet(), listener)).isInstanceOf(JDABuilder::class.java)
    }

    @Test
    fun `a source that announces nothing takes a listener and ignores it`() {
        VoiceServerSource { null }.onChange { error("never called") }
    }

    @Test
    fun `lists the text channels, and makes an invite into one once`() {
        val invite: Invite = mock { on { url } doReturn "https://discord.gg/abc" }
        val action: InviteAction = mock()
        whenever(action.setMaxAge(0)).thenReturn(action)
        whenever(action.setUnique(false)).thenReturn(action)
        whenever(action.complete()).thenReturn(invite)
        val welcome: TextChannel =
            mock {
                on { id } doReturn "481"
                on { name } doReturn "welcome"
                on { createInvite() } doReturn action
            }
        whenever(guild.textChannels).thenReturn(listOf(welcome))
        whenever(jda.getTextChannelById("481")).thenReturn(welcome)
        val source = source(applicationWith(0))
        assertThat(source.textRooms()).isEmpty()
        source.start()

        assertThat(source.textRooms()).containsExactly(TextRoom("481", "324", "welcome"))
        assertThat(source.invite("481")).isEqualTo("https://discord.gg/abc")
        assertThat(source.invite("481")).isEqualTo("https://discord.gg/abc")
        verify(welcome, times(1)).createInvite()
    }

    @Test
    fun `makes no invite where Discord refuses, or before it connects`() {
        val source = source(applicationWith(0))
        assertThat(source.invite("481")).isNull()

        whenever(jda.getTextChannelById("481")).thenThrow(IllegalStateException("missing permission"))
        source.start()

        assertThat(source.invite("481")).isNull()
    }
}
