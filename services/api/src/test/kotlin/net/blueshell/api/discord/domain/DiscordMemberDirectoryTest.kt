package net.blueshell.api.discord.domain

import net.blueshell.clients.discord.api.DiscordApi
import net.blueshell.clients.discord.model.GuildMemberResponse
import net.blueshell.clients.discord.model.UserResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
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
        bot: Boolean? = null,
    ): GuildMemberResponse {
        val user: UserResponse =
            mock {
                on { this.id } doReturn id
                on { this.username } doReturn username
                on { this.globalName } doReturn globalName
                on { avatar } doReturn userAvatar
                on { this.bot } doReturn bot
            }
        return mock {
            on { this.user } doReturn user
            on { this.nick } doReturn nick
            on { avatar } doReturn serverAvatar
        }
    }

    private fun directory(
        api: DiscordApi?,
        claimed: Set<String> = emptySet(),
    ): DiscordMemberDirectory {
        val provider: ObjectProvider<DiscordApi> = mock { on { ifAvailable } doReturn api }
        return DiscordMemberDirectory(provider, { claimed }, "324")
    }

    @Test
    fun `finds members by what somebody typed, trimmed, ten at most`() {
        val found = listOf(member(nick = "Nelly B"), member(id = "9", username = "botty", bot = true))
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

    @Test
    fun `lists everybody nobody has linked, by name, bots left out, across Discord's pages`() {
        val first = List(1000) { member(id = "${1000 + it}", username = "user$it") } + member(id = "3", username = "zed", bot = true)
        val firstPage = first.take(1000)
        val secondPage = listOf(member(id = "5000", username = "Anna"), member(id = "5001", username = "bea"))
        val api: DiscordApi =
            mock {
                on { listGuildMembers("324", 1000, null) } doReturn firstPage
                on { listGuildMembers("324", 1000, "1999") } doReturn secondPage
            }

        val unclaimed = directory(api, claimed = setOf("5001", "1000")).unclaimed()!!

        assertThat(unclaimed).hasSize(1000)
        assertThat(unclaimed.first().name).isEqualTo("Anna")
        assertThat(unclaimed.map { it.id }).doesNotContain("5001", "1000")
    }

    @Test
    fun `keeps the list rather than asking Discord on every read`() {
        val page = listOf(member(id = "5000", username = "anna"))
        val api: DiscordApi = mock { on { listGuildMembers("324", 1000, null) } doReturn page }
        val members = directory(api)

        members.unclaimed()
        assertThat(members.everyoneKept()!!.map { it.username }).containsExactly("anna")
        verify(api, times(1)).listGuildMembers("324", 1000, null)
    }

    @Test
    fun `lists nothing without a bot, or where Discord never answered`() {
        val failing: DiscordApi = mock { on { listGuildMembers("324", 1000, null) } doThrow IllegalStateException("down") }

        assertThat(directory(null).unclaimed()).isNull()
        assertThat(directory(failing).unclaimed()).isNull()
    }

    @Test
    fun `stops after ten pages rather than paging forever`() {
        val fullPage = List(1000) { member(id = "${10_000 + it}", username = "user$it") }
        val api: DiscordApi = mock { on { listGuildMembers(eq("324"), eq(1000), anyOrNull()) } doReturn fullPage }

        assertThat(directory(api).unclaimed()).hasSize(10_000)
    }

    @Test
    fun `reads everybody afresh when asked for them now, whatever it kept`() {
        val page = listOf(member(id = "5000", username = "anna"))
        val api: DiscordApi = mock { on { listGuildMembers("324", 1000, null) } doReturn page }
        val members = directory(api)

        members.unclaimed()
        members.everyoneNow()

        verify(api, times(2)).listGuildMembers("324", 1000, null)
    }

    @Test
    fun `names the members a description mentions, and leaves out who left`() {
        val listed = listOf(member(id = "11", username = "anna"), member(id = "12", username = "bea"))
        val api: DiscordApi = mock { on { listGuildMembers("324", 1000, null) } doReturn listed }

        assertThat(directory(api).named(setOf("12", "99"))!!.map { it.name }).containsExactly("bea")
        assertThat(directory(null).named(setOf("12"))).isNull()
    }
}
