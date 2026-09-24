package net.blueshell.api.sync.domain

import net.blueshell.api.jobs.api.JobOutcome
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.testsupport.runJob
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tools.jackson.databind.ObjectMapper

class SyncContactJobTest {
    private val objectMapper = ObjectMapper()
    private val contactSync: ContactSyncService = mock()
    private val job = SyncContactJob(objectMapper, contactSync)

    @Test
    fun `delegates to ContactSyncService with the payload userId`() {
        job.runJob(objectMapper.writeValueAsString(ContactJobs.SyncContactPayload(42L)))

        verify(contactSync).sync(eq(42L))
    }

    @Test
    fun `is skipped with the reason nothing was pushed`() {
        whenever(contactSync.sync(42L)).thenReturn("The user no longer exists.")

        assertThat(job.runJob(objectMapper.writeValueAsString(ContactJobs.SyncContactPayload(42L))))
            .isEqualTo(JobOutcome.Skipped("The user no longer exists."))
    }
}
