package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.cohort.persistence.Target
import net.blueshell.api.cohort.persistence.TargetKind
import net.blueshell.api.cohort.persistence.TargetMember
import net.blueshell.api.cohort.persistence.TargetMemberRepository
import net.blueshell.api.shared.enums.TargetSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class UnlinkedTargetsTest {
    private val members: TargetMemberRepository = mock()
    private val discord: TargetStrategy = mock()
    private val brevo: TargetStrategy = mock()
    private val cohort = Cohort(type = CohortType.COMMITTEE_MEMBERS, label = "Sitecie")

    private fun row(
        system: String,
        label: String,
    ) = TargetMember(target = Target(system = system, kind = TargetKind.ROLE, label = label), userId = 7, cohort = cohort)

    private fun unlinked(): UnlinkedTargets {
        whenever(discord.system).thenReturn(TargetSystem.DISCORD)
        whenever(brevo.system).thenReturn(TargetSystem.BREVO)
        return UnlinkedTargets(members, TargetStrategies(listOf(discord, brevo)))
    }

    @Test
    fun `names the Discord roles somebody belongs on while they have no Discord account linked`() {
        val rows = listOf(row("DISCORD", "Sitecie"), row("DISCORD", "Member"), row("DISCORD", "Member"), row("BREVO", "Members"))
        whenever(members.findAllByUserIdAndUserIdIsNotNull(7)).thenReturn(rows)
        whenever(discord.makesMemberIds).thenReturn(false)
        whenever(discord.memberIds(setOf(7L))).thenReturn(emptyMap())
        whenever(brevo.makesMemberIds).thenReturn(true)

        assertThat(unlinked().of(7)).containsExactly(
            UnlinkedTarget(TargetSystem.DISCORD, "Member"),
            UnlinkedTarget(TargetSystem.DISCORD, "Sitecie"),
        )
    }

    @Test
    fun `names nothing once the account is linked, nor for a system this build does not know`() {
        val rows = listOf(row("DISCORD", "Sitecie"), row("GONE", "Old"))
        whenever(members.findAllByUserIdAndUserIdIsNotNull(7)).thenReturn(rows)
        whenever(discord.makesMemberIds).thenReturn(false)
        whenever(discord.memberIds(setOf(7L))).thenReturn(mapOf(7L to "123"))

        assertThat(unlinked().of(7)).isEmpty()
    }

    @Test
    fun `names nothing for a known system without a strategy`() {
        whenever(members.findAllByUserIdAndUserIdIsNotNull(7)).thenReturn(listOf(row("GOOGLE_CALENDAR", "Board")))

        assertThat(UnlinkedTargets(members, TargetStrategies(emptyList())).of(7)).isEmpty()
    }
}
