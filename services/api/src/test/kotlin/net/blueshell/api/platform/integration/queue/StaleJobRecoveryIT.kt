package net.blueshell.api.platform.integration.queue

import net.blueshell.api.platform.integration.job.persistence.JobExecution
import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.testsupport.ServiceTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Integration tests for [StaleJobRecovery].
 *
 * Uses batch size = 2 to verify the batch limit is respected.
 * The key invariant verified is `queuedAt`: recovery synchronously resets
 * `queuedAt = Instant.now()` for stale RUNNING jobs before dispatching async,
 * making it a race-free assertion point.
 */
@Import(JobExecutorITConfig::class)
@TestPropertySource(properties = ["app.jobs.stale-recovery-batch-size=2"])
class StaleJobRecoveryIT : ServiceTestSupport() {

    @Autowired
    private lateinit var staleJobRecovery: StaleJobRecovery

    @Test
    fun `recovers stale RUNNING job beyond threshold`() {
        val execution = saveStaleRunningJob()

        staleJobRecovery.recoverStaleJobs()

        // queuedAt is reset by recovery to Instant.now(); DB truncates to seconds,
        // so compare against a threshold well before recovery rather than a precise instant.
        val updated = jobExecutions.findById(execution.id!!).orElseThrow()
        assertThat(updated.queuedAt).isAfter(Instant.now().minus(1, ChronoUnit.HOURS))
    }

    @Test
    fun `does not touch recent RUNNING job`() {
        val execution = saveJobExecution(
            status = JobExecutionStatus.RUNNING,
            startedAt = Instant.now().minus(10, ChronoUnit.MINUTES)
        )
        val originalQueuedAt = execution.queuedAt

        staleJobRecovery.recoverStaleJobs()

        val updated = jobExecutions.findById(execution.id!!).orElseThrow()
        assertThat(updated.status).isEqualTo(JobExecutionStatus.RUNNING)
        assertThat(updated.queuedAt).isEqualTo(originalQueuedAt)
    }

    @Test
    fun `respects staleRecoveryBatchSize limit`() {
        repeat(3) { saveStaleRunningJob() }

        staleJobRecovery.recoverStaleJobs()

        // Recovery resets queuedAt to Instant.now() for recovered jobs; unrecovered jobs
        // keep their original queuedAt (2 hours ago). Compare against 1 hour ago to
        // avoid DB second-truncation precision issues.
        val oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS)
        val recoveredCount = jobExecutions.findByJobType(TEST_JOB_TYPE)
            .count { it.queuedAt?.isAfter(oneHourAgo) == true }
        assertThat(recoveredCount).isEqualTo(2)
    }

    private fun saveStaleRunningJob(): JobExecution =
        saveJobExecution(
            status = JobExecutionStatus.RUNNING,
            startedAt = Instant.now().minus(2, ChronoUnit.HOURS)
        )

    private fun saveJobExecution(
        status: JobExecutionStatus,
        startedAt: Instant? = null
    ): JobExecution {
        val execution = JobExecution(
            jobType = TEST_JOB_TYPE,
            status = status,
            startedAt = startedAt,
            queuedAt = Instant.now().minus(2, ChronoUnit.HOURS)
        )
        return persist(execution)
    }

    companion object {
        private const val TEST_JOB_TYPE = "test.stale.job"
    }
}
