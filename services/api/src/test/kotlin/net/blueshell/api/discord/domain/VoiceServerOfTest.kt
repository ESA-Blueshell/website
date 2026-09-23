package net.blueshell.api.discord.domain

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.cache.CacheView
import net.dv8tion.jda.internal.entities.GuildImpl
import net.dv8tion.jda.internal.entities.MemberPresenceImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class VoiceServerOfTest {
    private val everyone: Role = mock()

    private fun member(name: String, avatar: String): Member =
        mock {
            on { effectiveName } doReturn name
            on { effectiveAvatarUrl } doReturn avatar
        }

    private fun channel(id: String, name: String, position: Int, view: Boolean, connect: Boolean, vararg people: Member): VoiceChannel {
        val channel: VoiceChannel = mock {
            on { this.id } doReturn id
            on { this.name } doReturn name
            on { positionRaw } doReturn position
            on { members } doReturn people.toList()
        }
        whenever(everyone.hasPermission(channel, Permission.VIEW_CHANNEL)).thenReturn(view)
        whenever(everyone.hasPermission(channel, Permission.VOICE_CONNECT)).thenReturn(connect)
        return channel
    }

    @Test
    fun `lists the rooms everybody can see with who is in them, locked where only some may join`() {
        val afk = channel("1", "AFK", 0, view = true, connect = true)
        val general = channel("2", "General", 1, view = true, connect = true, member("Emma", "https://cdn/emma.png"))
        val lounge = channel("3", "Members lounge", 2, view = true, connect = false, member("Mo", "https://cdn/embed/avatars/1.png"))
        val board = channel("4", "Board", 3, view = false, connect = false, member("Chair", "https://cdn/chair.png"))
        val guild: Guild = mock {
            on { id } doReturn "324"
            on { name } doReturn "Blueshell Esports"
            on { publicRole } doReturn everyone
            on { afkChannel } doReturn afk
            on { voiceChannels } doReturn listOf(afk, general, lounge, board)
        }

        val server = voiceServerOf(guild)

        assertThat(server.id).isEqualTo("324")
        assertThat(server.name).isEqualTo("Blueshell Esports")
        assertThat(server.rooms).containsExactly(
            VoiceRoom("2", "General", 1, locked = false, people = listOf(VoicePerson("Emma", "https://cdn/emma.png"))),
            VoiceRoom("3", "Members lounge", 2, locked = true, people = listOf(VoicePerson("Mo", "https://cdn/embed/avatars/1.png"))),
        )
    }

    @Test
    fun `keeps every visible room where the server has no AFK room`() {
        val general = channel("2", "General", 1, view = true, connect = true)
        val guild: Guild = mock {
            on { id } doReturn "324"
            on { name } doReturn "Blueshell"
            on { publicRole } doReturn everyone
            on { afkChannel } doReturn null
            on { voiceChannels } doReturn listOf(general)
        }

        assertThat(voiceServerOf(guild).rooms.map { it.id }).containsExactly("2")
    }

    @Test
    fun `counts only what the gateway keeps current`() {
        val presences: CacheView.SimpleCacheView<MemberPresenceImpl> = mock { on { size() } doReturn 269L }
        val guild: GuildImpl = mock {
            on { id } doReturn "324"
            on { name } doReturn "Blueshell"
            on { publicRole } doReturn everyone
            on { voiceChannels } doReturn emptyList()
            on { memberCount } doReturn 1199
            on { presenceView } doReturn presences
        }

        val counted = voiceServerOf(guild, countsOnline = true, countsMembers = true)
        assertThat(counted.online to counted.members).isEqualTo(269 to 1199)

        val uncounted = voiceServerOf(guild)
        assertThat(uncounted.online to uncounted.members).isEqualTo(null to null)
    }

    @Test
    fun `asks only for the privileged intents the application has, limited or not`() {
        assertThat(privilegedIntentsOf(0)).isEmpty()
        assertThat(privilegedIntentsOf(1 shl 12)).containsExactly(GatewayIntent.GUILD_PRESENCES)
        assertThat(privilegedIntentsOf(1 shl 15)).containsExactly(GatewayIntent.GUILD_MEMBERS)
        assertThat(privilegedIntentsOf((1 shl 13) or (1 shl 14) or (1 shl 19)))
            .containsExactlyInAnyOrder(GatewayIntent.GUILD_PRESENCES, GatewayIntent.GUILD_MEMBERS)
    }
}
