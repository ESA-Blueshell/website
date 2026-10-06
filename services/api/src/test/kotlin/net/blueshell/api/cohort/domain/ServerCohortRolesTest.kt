package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.discord.api.ChannelOpening
import net.blueshell.api.discord.api.DiscordChannelKeeper
import net.blueshell.api.discord.api.DiscordRoleAccess
import net.blueshell.api.discord.api.DiscordRoleKeeper
import net.blueshell.api.discord.api.KeptChannel
import net.blueshell.api.discord.api.KeptChannelKind
import net.blueshell.api.discord.api.KeptRole
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.env.MockEnvironment
import org.springframework.web.server.ResponseStatusException

class ServerCohortRolesTest {
    private val cohorts: CohortRepository = mock()
    private val targets: TargetRepository = mock()
    private val targetIds: CohortTargetIds = mock()
    private val registrar: CohortRegistrar = mock()
    private val discord: CohortDiscord = mock()
    private val roles: DiscordRoleKeeper = mock()
    private val channels: DiscordChannelKeeper = mock()
    private val access: DiscordRoleAccess = mock()
    private val members = Entities.cohort(id = 1, type = CohortType.CURRENT_MEMBERS, label = "Members")
    private val committee = Entities.cohort(id = 2, type = CohortType.CURRENT_COMMITTEE_MEMBERS, label = "Committee members")

    private fun settings(): ServerCohortRoles {
        val environment = MockEnvironment().withProperty("discord.default-channels.CURRENT_COMMITTEE_MEMBERS", "Activists, ")
        return ServerCohortRoles(
            cohorts,
            targets,
            targetIds,
            registrar,
            discord,
            roles,
            channels,
            access,
            "Committees",
            "Esports",
            "Board",
            environment,
        )
    }

    private fun place(roleId: String?) = DiscordPlace(true, roleId, roleId?.let { "Role $it" }, emptyList())

    @Test
    fun `lists the server-wide cohorts with the role each follows and its default channels`() {
        whenever(roles.available()).thenReturn(true)
        whenever(channels.available()).thenReturn(true)
        val lounge = KeptChannel("800", "lounge", KeptChannelKind.VOICE, null)
        val board = KeptChannel("801", "board", KeptChannelKind.TEXT, "Board")
        whenever(channels.openings())
            .thenReturn(listOf(ChannelOpening(lounge, false, listOf("111")), ChannelOpening(board, true, listOf("111"))))
        whenever(channels.openedTo("111")).thenReturn(listOf(lounge, board, KeptChannel("802", "Board", KeptChannelKind.CATEGORY, null)))
        whenever(cohorts.findByDefinitionKey("CURRENT_MEMBERS")).thenReturn(members)
        whenever(cohorts.findByDefinitionKey("CURRENT_COMMITTEE_MEMBERS")).thenReturn(committee)
        val linked = Entities.target(id = 10, system = TargetSystem.DISCORD.name, cohortId = 1)
        whenever(targets.findByCohortIdAndSystem(1, TargetSystem.DISCORD.name)).thenReturn(linked)
        whenever(targetIds.find(linked)).thenReturn("111")
        whenever(roles.role("111")).thenReturn(KeptRole("111", "Blueshell's Finest", true))

        val listed = settings().read()

        assertThat(listed.map { it.key }).containsExactly("CURRENT_MEMBERS", "CURRENT_COMMITTEE_MEMBERS")
        assertThat(listed[0]).isEqualTo(
            ServerCohortRole(
                "CURRENT_MEMBERS",
                CohortType.CURRENT_MEMBERS,
                "Members",
                "111",
                "Blueshell's Finest",
                emptyList(),
                listOf(
                    CohortChannel("800", "lounge", voice = true, private = false),
                    CohortChannel("801", "board", voice = false, private = true),
                ),
            ),
        )
        assertThat(listed[0].channels.map { it.private }).containsExactly(false, true)
        assertThat(listed[1].roleId).isNull()
        assertThat(listed[1].defaultChannels).containsExactly("Activists")
        // Activists, the esports team members, the board and Kandi have no record here, so the page registered the definitions.
        verify(registrar).register()

        whenever(roles.available()).thenReturn(false)
        assertThat(settings().read().first().roleName).isNull()
        assertThat(settings().read().first().channels).isEmpty()
    }

    @Test
    fun `a role a cohort gets now is opened to its default channels, under the cohort's own category`() {
        val choice = DiscordChoice(createRole = true, createChannel = "committee-members")
        whenever(discord.read("CURRENT_COMMITTEE_MEMBERS")).thenReturn(place(null), place("222"))
        whenever(discord.apply("CURRENT_COMMITTEE_MEMBERS", choice, "Committees")).thenReturn(place("222"))
        whenever(channels.channels()).thenReturn(
            listOf(
                KeptChannel("900", "activists", KeptChannelKind.TEXT, "Activists"),
                KeptChannel("901", "activists", KeptChannelKind.CATEGORY, null),
                KeptChannel("902", "general", KeptChannelKind.TEXT, null),
            ),
        )

        assertThat(settings().apply("CURRENT_COMMITTEE_MEMBERS", choice).roleId).isEqualTo("222")

        verify(access).openForWriting("222", "900")
        verify(access, never()).openForWriting("222", "901")
        verify(access, never()).openForWriting("222", "902")
    }

    @Test
    fun `a cohort that had its role, or still has none, opens nothing more, and each kind of cohort has its category`() {
        val channelsOnly = DiscordChoice(channelIds = listOf("903"))
        whenever(discord.read("CURRENT_COMMITTEE_MEMBERS")).thenReturn(place("222"))
        whenever(discord.apply("CURRENT_COMMITTEE_MEMBERS", channelsOnly, "Committees")).thenReturn(place("222"))
        settings().apply("CURRENT_COMMITTEE_MEMBERS", channelsOnly)

        whenever(discord.read("CURRENT_TEAM_PLAYERS")).thenReturn(place(null))
        whenever(discord.apply("CURRENT_TEAM_PLAYERS", channelsOnly, "Esports")).thenReturn(place(null))
        settings().apply("CURRENT_TEAM_PLAYERS", channelsOnly)

        whenever(discord.read("KANDI")).thenReturn(place("333"))
        whenever(discord.apply("KANDI", channelsOnly, "Board")).thenReturn(place("333"))
        settings().apply("KANDI", channelsOnly)

        verify(discord).apply("CURRENT_TEAM_PLAYERS", channelsOnly, "Esports")
        verify(discord).apply("KANDI", channelsOnly, "Board")
        verify(access, never()).openForWriting(any(), any())

        // Members have no default channels, so a role they get now opens nothing extra.
        whenever(discord.read("CURRENT_MEMBERS")).thenReturn(place(null), place("111"))
        whenever(discord.apply("CURRENT_MEMBERS", channelsOnly, "Committees")).thenReturn(place("111"))
        settings().apply("CURRENT_MEMBERS", channelsOnly)
        verify(access, never()).openForWriting(any(), any())
    }

    @Test
    fun `reads a server-wide cohort's place, and refuses a cohort that is not server-wide`() {
        whenever(discord.read("BOARD")).thenReturn(place("444"))
        assertThat(settings().place("BOARD").roleId).isEqualTo("444")
        assertThatThrownBy { settings().place("COMMITTEE_MEMBERS:3") }.isInstanceOf(ResponseStatusException::class.java)
        assertThatThrownBy { settings().apply("TEAM_PLAYERS:4", DiscordChoice()) }.isInstanceOf(ResponseStatusException::class.java)
    }
}
