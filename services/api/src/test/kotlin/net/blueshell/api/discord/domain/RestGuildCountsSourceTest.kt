package net.blueshell.api.discord.domain

import net.blueshell.clients.discord.api.DiscordApi
import net.blueshell.clients.discord.model.GuildWithCountsResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RestGuildCountsSourceTest {
    private val discordApi: DiscordApi = mock()
    private val source = RestGuildCountsSource(discordApi, "324")

    private fun at(epochSecond: Long) {
        source.clock = Clock.fixed(Instant.ofEpochSecond(epochSecond), ZoneOffset.UTC)
    }

    private fun answers(members: Int?, online: Int?) {
        val guild: GuildWithCountsResponse = mock {
            on { approximateMemberCount } doReturn members
            on { approximatePresenceCount } doReturn online
        }
        // doReturn rather than whenever(...): re-stubbing through a call would hit the earlier stub.
        doReturn(guild).whenever(discordApi).getGuild(eq("324"), eq(true))
    }

    @Test
    fun `reads the counts with counts asked for, and keeps them for a minute`() {
        answers(members = 1199, online = 269)
        at(0)
        assertThat(source.counts()).isEqualTo(GuildCounts(members = 1199, online = 269))

        at(59)
        source.counts()
        verify(discordApi, times(1)).getGuild(any(), any())

        at(61)
        answers(members = 1200, online = 270)
        assertThat(source.counts()).isEqualTo(GuildCounts(members = 1200, online = 270))
    }

    @Test
    fun `keeps the last counts when a read fails, and has none before the first`() {
        at(0)
        doThrow(IllegalStateException("down")).whenever(discordApi).getGuild(any(), any())
        assertThat(source.counts()).isNull()

        answers(members = 10, online = 3)
        assertThat(source.counts()).isEqualTo(GuildCounts(10, 3))

        at(120)
        doThrow(IllegalStateException("down")).whenever(discordApi).getGuild(any(), any())
        assertThat(source.counts()).isEqualTo(GuildCounts(10, 3))
    }

    @Test
    fun `treats an answer without counts as no answer`() {
        at(0)
        answers(members = null, online = 5)
        assertThat(source.counts()).isNull()
    }
}
