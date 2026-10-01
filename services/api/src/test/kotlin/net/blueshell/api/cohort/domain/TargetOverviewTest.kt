package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.cohort.persistence.TargetKind
import net.blueshell.api.cohort.persistence.TargetMemberRepository
import net.blueshell.api.cohort.persistence.TargetReconcileRun
import net.blueshell.api.cohort.persistence.TargetReconcileRunRepository
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class TargetOverviewTest {
    private val catalog = mock<TargetCatalog>()
    private val targets = mock<TargetRepository>()
    private val cohorts = mock<CohortRepository>()
    private val members = mock<TargetMemberRepository>()
    private val runs = mock<TargetReconcileRunRepository>()
    private val jobs = mock<JobQueue>()
    private val overview = TargetOverview(catalog, targets, cohorts, members, runs, jobs)

    private val paid = Entities.cohort(id = 1, type = CohortType.PERIOD_PAYERS, label = "Paid 2025-2026")
    private val newPaid = Entities.cohort(id = 2, type = CohortType.PERIOD_PAYERS, label = "Paid 2026-2027")
    private val linked = Entities.target(id = 10, cohortId = 1, externalId = "7").also { it.enforced = true }
    private val unmade = Entities.target(id = 11, cohortId = 2, folder = "Contribution paid")
    private val discord = Entities.target(id = 12, system = "GOOGLE", cohortId = 2)

    private fun given() {
        whenever(catalog.search(TargetSystem.BREVO, null)).thenReturn(
            listOf(
                ExternalTarget(TargetSystem.BREVO, "7", TargetKind.LIST, "Contribution paid 2025-2026", "Contribution paid", 188),
                ExternalTarget(TargetSystem.BREVO, "8", TargetKind.LIST, "Old newsletter test", null, 4),
            ),
        )
        whenever(targets.findAllBySystem("BREVO")).thenReturn(listOf(linked, unmade))
        whenever(targets.findAllByCohortIdIsNotNullAndExternalIdIsNull()).thenReturn(listOf(unmade, discord))
        whenever(cohorts.findAllById(setOf(1L, 2L))).thenReturn(listOf(paid, newPaid))
        whenever(runs.findFirstByTargetIdOrderByStartedAtDesc(10)).thenReturn(
            TargetReconcileRun(10, Instant.parse("2026-10-01T03:00:00Z"), null, 186, 1, 2),
        )
        whenever(members.countByTargetIdAndUserIdIsNotNull(11)).thenReturn(142)
    }

    @Test
    fun `lists every list with the cohort it follows and its newest drift, and the expected lists that are missing`() {
        given()

        val read = overview.of(TargetSystem.BREVO)

        assertThat(read.lists).containsExactly(
            ListedTarget(
                "7",
                "Contribution paid 2025-2026",
                "Contribution paid",
                188,
                10,
                1,
                "Paid 2025-2026",
                CohortType.PERIOD_PAYERS,
                1,
                2,
                Instant.parse("2026-10-01T03:00:00Z"),
                true,
            ),
            ListedTarget("8", "Old newsletter test", null, 4, null, null, null, null, null, null, null, false),
        )
        assertThat(read.missing).containsExactly(
            MissingTarget(11, 2, "Paid 2026-2027", CohortType.PERIOD_PAYERS, "Contribution paid", 142, false),
        )
        assertThat(read.lastReconciledAt).isEqualTo(Instant.parse("2026-10-01T03:00:00Z"))
    }

    @Test
    fun `creates the named missing lists or every one, each as its own job, and only on that system`() {
        given()
        unmade.targetClaimedAt = Instant.EPOCH

        assertThat(
            overview
                .of(TargetSystem.BREVO)
                .missing
                .single()
                .creating,
        ).isTrue()
        assertThat(overview.createMissing(TargetSystem.BREVO, listOf(99))).isZero()
        assertThat(overview.createMissing(TargetSystem.BREVO, emptyList())).isEqualTo(1)
        verify(
            jobs,
        ).runAsync(eq(CohortJobs.CreateCohortTarget), eq(CohortJobs.CreateCohortTargetPayload(11)), eq(JobTrigger.SITE_ACTION), eq(null))
        verify(
            jobs,
            never(),
        ).runAsync(eq(CohortJobs.CreateCohortTarget), eq(CohortJobs.CreateCohortTargetPayload(12)), eq(JobTrigger.SITE_ACTION), eq(null))
    }
}
