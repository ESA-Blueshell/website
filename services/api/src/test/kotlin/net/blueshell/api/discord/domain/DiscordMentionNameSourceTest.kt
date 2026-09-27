package net.blueshell.api.discord.domain

import net.blueshell.api.shared.discord.MentionIds
import net.blueshell.api.shared.discord.MentionNames
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions

class DiscordMentionNameSourceTest {
    private val members: DiscordMemberDirectory =
        mock { on { named(any()) } doReturn listOf(DiscordMember("11", "Anna", "anna", "https://cdn/anna.png")) }
    private val roles: DiscordRoleDirectory = mock { on { named(any()) } doReturn listOf(DiscordRoleName("901", "Gamers", null)) }
    private val channels: DiscordChannelDirectory =
        mock { on { open() } doReturn listOf(DiscordChannel("1", "general"), DiscordChannel("2", "events-info")) }

    @Test
    fun `names what a description mentions from what the directories keep`() {
        val named = DiscordMentionNameSource(members, roles, channels).named(MentionIds(setOf("11"), setOf("901"), setOf("2")))

        assertThat(named).isEqualTo(MentionNames(mapOf("11" to "Anna"), mapOf("901" to "Gamers"), mapOf("2" to "events-info")))
    }

    @Test
    fun `asks nothing for what is not mentioned, and nothing comes back where Discord cannot be asked`() {
        val quiet: DiscordMemberDirectory = mock()
        val none: DiscordRoleDirectory = mock()
        val shut: DiscordChannelDirectory = mock()
        val nothing = MentionIds(emptySet(), emptySet(), emptySet())
        assertThat(DiscordMentionNameSource(quiet, none, shut).named(nothing)).isEqualTo(MentionNames())
        verifyNoInteractions(quiet, none, shut)

        val offline: DiscordChannelDirectory = mock { on { open() } doReturn null }
        assertThat(DiscordMentionNameSource(members, roles, offline).named(MentionIds(emptySet(), emptySet(), setOf("2")))).isNull()
        val noRoles: DiscordRoleDirectory = mock { on { named(any()) } doReturn null }
        assertThat(DiscordMentionNameSource(members, noRoles, channels).named(MentionIds(emptySet(), setOf("1"), emptySet()))).isNull()
        val noMembers: DiscordMemberDirectory = mock { on { named(any()) } doReturn null }
        assertThat(DiscordMentionNameSource(noMembers, roles, channels).named(MentionIds(setOf("1"), emptySet(), emptySet()))).isNull()
    }
}
