package net.blueshell.api.cohort.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.shared.job.JobDefinition
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.testsupport.Entities
import org.junit.jupiter.api.Test

class CohortVerificationSchedulerTest {
    @Test
    fun `queues a check of every cohort bound to an external list, and of no other`() {
        val bound: Cohort = Entities.cohort(id = 5L)
        val unbound: Cohort = Entities.cohort()
        val cohorts: CohortRepository = mockk { every { findAll() } returns mutableListOf(bound, unbound) }
        val targetIds: CohortTargetIds =
            mockk {
                every { find(bound) } returns "list-1"
                every { find(unbound) } returns null
            }
        val jobs: JobQueue = mockk(relaxed = true)

        CohortVerificationScheduler(cohorts, targetIds, jobs).verifyAllCohorts()

        verify(exactly = 1) { jobs.runAsync(any<JobDefinition<CohortJobs.ReconcileListPayload>>(), any(), any(), any()) }
        val payload = CohortJobs.ReconcileListPayload(5L, JobTrigger.SCHEDULED_RUN)
        verify { jobs.runAsync(CohortJobs.ReconcileList, payload, JobTrigger.SCHEDULED_RUN) }
    }
}
