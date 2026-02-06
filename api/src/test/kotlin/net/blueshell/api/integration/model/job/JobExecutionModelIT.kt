package net.blueshell.api.integration.model.job

import net.blueshell.api.integration.model.ModelPersistenceTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class JobExecutionModelIT : net.blueshell.api.integration.model.ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun persists_column_fields() {
            val job = JobExecution(
                jobType = "nightly",
                status = JobExecutionStatus.RUNNING,
                payload = "{\"key\":\"value\"}",
                errorMessage = "none",
                attempts = 3,
                queuedAt = timestamp(),
                startedAt = timestamp().plusSeconds(60),
                finishedAt = timestamp().plusSeconds(120)
            )

            val found = persistAndReload(job, JobExecution::class.java) { it.id }

            assertEquals(job.jobType, found.jobType)
            assertEquals(job.status, found.status)
            assertEquals(job.payload, found.payload)
            assertEquals(job.errorMessage, found.errorMessage)
            assertEquals(job.attempts, found.attempts)
            assertEquals(job.queuedAt, found.queuedAt)
            assertEquals(job.startedAt, found.startedAt)
            assertEquals(job.finishedAt, found.finishedAt)
        }
    }
}
