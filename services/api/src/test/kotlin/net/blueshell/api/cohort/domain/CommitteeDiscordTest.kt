package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.discord.api.DiscordChannelKeeper
import net.blueshell.api.discord.api.DiscordRoleKeeper
import net.blueshell.api.discord.api.DiscordUnavailable
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
import org.springframework.web.server.ResponseStatusException
import java.util.Optional

class CommitteeDiscordTest {
    private val cohorts: CohortRepository = mock()
    private val targets: TargetRepository = mock()
    private val targetIds: CohortTargetIds = mock()
    private val targeting: CohortTargeting = mock()
    private val registrar: CohortRegistrar = mock()
    private val roles: DiscordRoleKeeper = mock()
    private val channels: DiscordChannelKeeper = mock()
    private val discord = CommitteeDiscord(cohorts, targets, targetIds, targeting, registrar, roles, channels, "Committees")
    private val cohort = Entities.cohort(id = 5, type = CohortType.COMMITTEE_MEMBERS, label = "Sitecie")
    private val role = Entities.target(id = 50, system = "DISCORD", cohortId = 5, externalId = "900")
    private val sitecie = KeptChannel("1", "sitecie", KeptChannelKind.TEXT, "Committees")

    private fun given(linked: Boolean) {
        whenever(roles.available()).thenReturn(true)
        whenever(channels.available()).thenReturn(true)
        whenever(cohorts.findByDefinitionKey("COMMITTEE_MEMBERS:7")).thenReturn(cohort)
        whenever(cohorts.findById(5)).thenReturn(Optional.of(cohort))
        whenever(targets.findByCohortIdAndSystem(5, "DISCORD")).thenReturn(if (linked) role else null)
        whenever(targetIds.find(role)).thenReturn("900")
        whenever(roles.role("900")).thenReturn(KeptRole("900", "Sitecie", true))
    }

    @Test
    fun `reads the role the seats hold and the channels it opens, and nothing without a bot`() {
        given(linked = true)
        whenever(channels.openedTo("900")).thenReturn(listOf(sitecie))

        assertThat(discord.read(7)).isEqualTo(CommitteeDiscordState(true, "900", "Sitecie", listOf(sitecie)))

        whenever(roles.available()).thenReturn(false)
        assertThat(discord.read(7)).isEqualTo(CommitteeDiscordState(false, null, null, emptyList()))
        assertThatThrownBy { discord.apply(7, CommitteeDiscordChoice(createRole = true)) }.isInstanceOf(TargetSystemUnavailable::class.java)
    }

    @Test
    fun `a new committee by default gets a new role and a private channel under Committees`() {
        given(linked = false)
        whenever(targeting.create(5, TargetSystem.DISCORD, "Sitecie", null)).thenReturn(CohortTargetRow(role, "900"))
        whenever(channels.openedTo("900")).thenReturn(emptyList())

        discord.apply(7, CommitteeDiscordChoice(createRole = true, createChannel = " sitecie "))

        verify(channels).createPrivate("sitecie", "Committees", "900")
    }

    @Test
    fun `links an existing role, opens the channels chosen and closes the ones left out, keeping a linked role`() {
        given(linked = false)
        whenever(targeting.linkExisting(5, TargetSystem.DISCORD, "901")).thenReturn(CohortTargetRow(role, "901"))
        whenever(channels.openedTo("901")).thenReturn(listOf(sitecie))

        discord.apply(7, CommitteeDiscordChoice(roleId = "901", channelIds = listOf("2")))

        verify(channels).open("2", "901", true)
        verify(channels).close("1", "901")

        given(linked = true)
        whenever(channels.openedTo("900")).thenReturn(emptyList())
        discord.apply(7, CommitteeDiscordChoice(roleId = "902", createChannel = " "))
        verify(targeting, never()).linkExisting(5, TargetSystem.DISCORD, "902")
        verify(channels, never()).createPrivate(any(), any(), any())
    }

    @Test
    fun `without a role nothing is opened, a cohort not yet registered is registered first, and a bot gone mid-call refuses`() {
        given(linked = false)
        assertThat(discord.apply(7, CommitteeDiscordChoice(channelIds = listOf("2"))).roleId).isNull()
        verify(channels, never()).open(any(), any(), any())

        whenever(cohorts.findByDefinitionKey("COMMITTEE_MEMBERS:8")).thenReturn(null, cohort)
        discord.read(8)
        verify(registrar).register()
        whenever(cohorts.findByDefinitionKey("COMMITTEE_MEMBERS:9")).thenReturn(null)
        assertThatThrownBy { discord.read(9) }.isInstanceOf(ResponseStatusException::class.java)

        given(linked = true)
        whenever(channels.openedTo("900")).thenThrow(DiscordUnavailable("gone"))
        assertThatThrownBy { discord.read(7) }.isInstanceOf(TargetSystemUnavailable::class.java)
    }
}
