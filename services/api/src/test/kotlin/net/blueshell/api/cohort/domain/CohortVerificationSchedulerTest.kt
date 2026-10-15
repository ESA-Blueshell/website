package net.blueshell.api.cohort.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.cohort.persistence.Target
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.shared.job.JobDefinition
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.testsupport.Entities
import org.junit.jupiter.api.Test

class CohortVerificationSchedulerTest {
    @Test
    fun `queues a check of every cohort bound to an external list, and of no other`() {
        val bound: Target = Entities.target(id = 5L)
        val unbound: Target = Entities.target()
        val targets: TargetRepository = mockk { every { findAll() } returns mutableListOf(bound, unbound) }
        val targetExternalIds: CohortTargetIds =
            mockk {
                every { find(bound) } returns "list-1"
                every { find(unbound) } returns null
            }
        val jobs: JobQueue = mockk(relaxed = true)

        CohortVerificationScheduler(targets, targetExternalIds, jobs).verifyAllTargets()

        verify(exactly = 1) { jobs.runAsync(any<JobDefinition<CohortJobs.ReconcileListPayload>>(), any(), any(), any()) }
        val payload = CohortJobs.ReconcileListPayload(5L, JobTrigger.SCHEDULED_RUN)
        verify { jobs.runAsync(CohortJobs.ReconcileList, payload, JobTrigger.SCHEDULED_RUN) }
    }
}
