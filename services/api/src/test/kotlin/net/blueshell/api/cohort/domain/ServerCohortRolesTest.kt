package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.cohort.persistence.TargetRepository
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
import org.mockito.kotlin.atLeastOnce
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
    private val targeting: CohortTargeting = mock()
    private val registrar: CohortRegistrar = mock()
    private val roles: DiscordRoleKeeper = mock()
    private val channels: DiscordChannelKeeper = mock()
    private val access: DiscordRoleAccess = mock()
    private val members = Entities.cohort(id = 1, type = CohortType.CURRENT_MEMBERS, label = "Members")
    private val committee = Entities.cohort(id = 2, type = CohortType.CURRENT_COMMITTEE_MEMBERS, label = "Committee members")

    private fun settings(): ServerCohortRoles {
        val environment = MockEnvironment().withProperty("discord.default-channels.CURRENT_COMMITTEE_MEMBERS", "Activists, ")
        return ServerCohortRoles(cohorts, targets, targetIds, targeting, registrar, roles, channels, access, environment)
    }

    private fun discordUp() {
        whenever(roles.available()).thenReturn(true)
        whenever(channels.available()).thenReturn(true)
    }

    @Test
    fun `lists the server-wide cohorts with the role each follows and its default channels`() {
        discordUp()
        whenever(cohorts.findByDefinitionKey("CURRENT_MEMBERS")).thenReturn(members)
        whenever(cohorts.findByDefinitionKey("CURRENT_COMMITTEE_MEMBERS")).thenReturn(committee)
        val linked = Entities.target(id = 10, system = TargetSystem.DISCORD.name, cohortId = 1)
        whenever(targets.findByCohortIdAndSystem(1, TargetSystem.DISCORD.name)).thenReturn(linked)
        whenever(targetIds.find(linked)).thenReturn("111")
        whenever(roles.role("111")).thenReturn(KeptRole("111", "Blueshell's Finest", true))

        val listed = settings().read()

        assertThat(listed.map { it.key }).containsExactly("CURRENT_MEMBERS", "CURRENT_COMMITTEE_MEMBERS")
        assertThat(listed[0]).isEqualTo(
            ServerCohortRole("CURRENT_MEMBERS", CohortType.CURRENT_MEMBERS, "Members", "111", "Blueshell's Finest", emptyList()),
        )
        assertThat(listed[1].roleId).isNull()
        assertThat(listed[1].defaultChannels).containsExactly("Activists")
        // Activists, the board and Kandi have no record here, so the page registered the definitions.
        verify(registrar).register()
    }

    @Test
    fun `links a role to a cohort with none, opening its default channels to it`() {
        discordUp()
        whenever(cohorts.findByDefinitionKey("CURRENT_COMMITTEE_MEMBERS")).thenReturn(committee)
        val row = CohortTargetRow(Entities.target(id = 11, system = TargetSystem.DISCORD.name, cohortId = 2), "222")
        whenever(targeting.linkExisting(2, TargetSystem.DISCORD, "222")).thenReturn(row)
        whenever(channels.channels()).thenReturn(
            listOf(
                KeptChannel("900", "activists", KeptChannelKind.TEXT, "Activists"),
                KeptChannel("901", "activists", KeptChannelKind.CATEGORY, null),
                KeptChannel("902", "general", KeptChannelKind.TEXT, null),
            ),
        )

        settings().set("CURRENT_COMMITTEE_MEMBERS", "222", create = false)

        verify(access).openForWriting("222", "900")
        verify(access, never()).openForWriting("222", "901")
        verify(access, never()).openForWriting("222", "902")
    }

    @Test
    fun `moves a cohort from the role it had to another, and creates one where asked`() {
        discordUp()
        whenever(cohorts.findByDefinitionKey("CURRENT_MEMBERS")).thenReturn(members)
        val linked = Entities.target(id = 10, system = TargetSystem.DISCORD.name, cohortId = 1)
        whenever(targets.findByCohortIdAndSystem(1, TargetSystem.DISCORD.name)).thenReturn(linked)
        whenever(targetIds.find(linked)).thenReturn("111")
        whenever(targeting.switchTarget(1, 10, "333", deletePrevious = false, reconcileNow = true))
            .thenReturn(CohortTargetRow(linked, "333"))

        settings().set("CURRENT_MEMBERS", "333", create = false)
        verify(targeting).switchTarget(1, 10, "333", deletePrevious = false, reconcileNow = true)

        whenever(targets.findByCohortIdAndSystem(1, TargetSystem.DISCORD.name)).thenReturn(null)
        whenever(targeting.create(1, TargetSystem.DISCORD, "Members", null)).thenReturn(CohortTargetRow(linked, "444"))
        settings().set("CURRENT_MEMBERS", null, create = true)
        verify(targeting).create(1, TargetSystem.DISCORD, "Members", null)
        verify(access, never()).openForWriting(any(), any())
    }

    @Test
    fun `refuses a cohort that is not server-wide, a request naming nothing, and a server without the bot`() {
        assertThatThrownBy { settings().set("COMMITTEE_MEMBERS:3", "1", create = false) }.isInstanceOf(ResponseStatusException::class.java)
        whenever(roles.available()).thenReturn(false)
        assertThatThrownBy { settings().set("CURRENT_MEMBERS", "1", create = false) }.isInstanceOf(TargetSystemUnavailable::class.java)
        discordUp()
        whenever(cohorts.findByDefinitionKey("CURRENT_MEMBERS")).thenReturn(members)
        assertThatThrownBy { settings().set("CURRENT_MEMBERS", null, create = false) }.isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `registers a cohort that has no record yet, and names no role without the bot`() {
        discordUp()
        whenever(cohorts.findByDefinitionKey("KANDI")).thenReturn(null, Entities.cohort(id = 5, type = CohortType.KANDI, label = "Kandi"))
        whenever(targeting.create(5, TargetSystem.DISCORD, "Kandi", null)).thenReturn(CohortTargetRow(Entities.target(id = 12), null))
        settings().set("KANDI", null, create = true)
        verify(registrar, atLeastOnce()).register()

        whenever(cohorts.findByDefinitionKey("BOARD")).thenReturn(null)
        assertThatThrownBy { settings().set("BOARD", null, create = true) }.isInstanceOf(ResponseStatusException::class.java)

        whenever(roles.available()).thenReturn(false)
        whenever(cohorts.findByDefinitionKey("CURRENT_MEMBERS")).thenReturn(members)
        val linked = Entities.target(id = 10, system = TargetSystem.DISCORD.name, cohortId = 1)
        whenever(targets.findByCohortIdAndSystem(1, TargetSystem.DISCORD.name)).thenReturn(linked)
        whenever(targetIds.find(linked)).thenReturn("111")
        assertThat(settings().read().single { it.key == "CURRENT_MEMBERS" }.roleName).isNull()
    }
}
