package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.cohort.persistence.Target
import net.blueshell.api.cohort.persistence.TargetKind
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.env.MockEnvironment

class KeptDiscordRolesTest {
    private val cohorts: CohortRepository = mock()
    private val targets: TargetRepository = mock()
    private val targetIds: CohortTargetIds = mock()
    private val jobs: JobQueue = mock()
    private val members = Entities.cohort(id = 1, type = CohortType.CURRENT_MEMBERS, label = "Members")
    private val activists = Entities.cohort(id = 2, type = CohortType.ACTIVISTS, label = "Activists")

    private fun kept(vararg roles: Pair<String, String>): KeptDiscordRoles {
        val environment = MockEnvironment()
        roles.forEach { (key, id) -> environment.setProperty("discord.cohort-roles.$key", id) }
        return KeptDiscordRoles(cohorts, targets, targetIds, jobs, environment)
    }

    @Test
    fun `links each named role to its cohort as its Discord target, once`() {
        whenever(cohorts.findByDefinitionKey("CURRENT_MEMBERS")).thenReturn(members)
        whenever(cohorts.findByDefinitionKey("ACTIVISTS")).thenReturn(activists)
        whenever(targets.save(any<Target>())).thenAnswer { (it.arguments[0] as Target).also { target -> target.id = 10 } }
        val unlinked = Entities.target(id = 20, system = "DISCORD", cohortId = 2)
        whenever(targets.findByCohortIdAndSystem(2, "DISCORD")).thenReturn(unlinked)

        assertThat(kept("CURRENT_MEMBERS" to " 111 ", "ACTIVISTS" to "222", "GONE" to "333", "BLANK" to " ").link()).isEqualTo(2)

        val made = argumentCaptor<Target>()
        verify(targets).save(made.capture())
        assertThat(listOf(made.firstValue.system, made.firstValue.kind, made.firstValue.label, made.firstValue.cohortId))
            .containsExactly("DISCORD", TargetKind.ROLE, "Members", 1L)
        verify(targetIds).record(made.firstValue, "111")
        verify(targetIds).record(unlinked, "222")
        verify(jobs, times(2)).runAsync(any<CohortJobs.ReconcileList>(), any(), any(), org.mockito.kotlin.anyOrNull())
    }

    @Test
    fun `a cohort already linked to a role keeps it, and nothing named links nothing`() {
        whenever(cohorts.findByDefinitionKey("CURRENT_MEMBERS")).thenReturn(members)
        val linked = Entities.target(id = 20, system = "DISCORD", cohortId = 1, externalId = "999")
        whenever(targets.findByCohortIdAndSystem(1, "DISCORD")).thenReturn(linked)
        whenever(targetIds.find(linked)).thenReturn("999")

        assertThat(kept("CURRENT_MEMBERS" to "111").link()).isZero()
        assertThat(kept().link()).isZero()
        verify(targetIds, never()).record(any(), any())
    }
}
