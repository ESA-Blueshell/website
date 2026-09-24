package net.blueshell.api.discord.domain

import net.blueshell.clients.discord.api.DiscordApi
import net.blueshell.clients.discord.model.GuildMemberResponse
import net.blueshell.clients.discord.model.UserResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.ObjectProvider

class DiscordMemberDirectoryTest {
    private fun member(
        id: String = "80351110224678912",
        username: String = "nelly",
        globalName: String? = null,
        nick: String? = null,
        userAvatar: String? = null,
        serverAvatar: String? = null,
    ): GuildMemberResponse {
        val user: UserResponse =
            mock {
                on { this.id } doReturn id
                on { this.username } doReturn username
                on { this.globalName } doReturn globalName
                on { avatar } doReturn userAvatar
            }
        return mock {
            on { this.user } doReturn user
            on { this.nick } doReturn nick
            on { avatar } doReturn serverAvatar
        }
    }

    private fun directory(api: DiscordApi?): DiscordMemberDirectory {
        val provider: ObjectProvider<DiscordApi> = mock { on { ifAvailable } doReturn api }
        return DiscordMemberDirectory(provider, "324")
    }

    @Test
    fun `finds members by what somebody typed, trimmed, ten at most`() {
        val found = listOf(member(nick = "Nelly B"))
        val api: DiscordApi = mock { on { searchGuildMembers("324", "nel", 10) } doReturn found }

        assertThat(directory(api).search("  nel ")).containsExactly(
            DiscordMember("80351110224678912", "Nelly B", "nelly", "https://cdn.discordapp.com/embed/avatars/5.png"),
        )
    }

    @Test
    fun `asks nothing of Discord for one character`() {
        val api: DiscordApi = mock()

        assertThat(directory(api).search("n")).isEmpty()
        verifyNoInteractions(api)
    }

    @Test
    fun `says nothing without a bot, or when Discord does not answer`() {
        val failing: DiscordApi = mock { on { searchGuildMembers("324", "nel", 10) } doThrow IllegalStateException("down") }

        assertThat(directory(null).search("nel")).isNull()
        assertThat(directory(failing).search("nel")).isNull()
    }

    @Test
    fun `shows the server's name for somebody, else theirs, else their username`() {
        assertThat(memberOf(member(nick = "Nelly B", globalName = "Nelly"), "324").name).isEqualTo("Nelly B")
        assertThat(memberOf(member(globalName = "Nelly"), "324").name).isEqualTo("Nelly")
        assertThat(memberOf(member(), "324").name).isEqualTo("nelly")
    }

    @Test
    fun `shows their server avatar, else their own, else Discord's default for them`() {
        assertThat(memberOf(member(serverAvatar = "s1", userAvatar = "u1"), "324").avatar)
            .isEqualTo("https://cdn.discordapp.com/guilds/324/users/80351110224678912/avatars/s1.png?size=64")
        assertThat(memberOf(member(userAvatar = "u1"), "324").avatar)
            .isEqualTo("https://cdn.discordapp.com/avatars/80351110224678912/u1.png?size=64")
    }
}
