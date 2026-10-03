package net.blueshell.api.user.domain

import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.user.api.UserJobs
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class SealedValueRewrapSchedulerTest {
    @Test
    fun `queues the rewrap as a scheduled run`() {
        val jobs: JobQueue = mock()

        SealedValueRewrapScheduler(jobs).rewrapSealedValues()

        verify(jobs).runAsync(UserJobs.RewrapSealedValues, UserJobs.RewrapSealedValuesPayload(), JobTrigger.SCHEDULED_RUN)
    }
}
