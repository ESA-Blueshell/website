package net.blueshell.api.platform.integration.job.repository

import net.blueshell.api.platform.integration.job.persistence.JobExecution
import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.shared.repository.BaseRepository
import java.time.Instant

interface JobExecutionRepository : BaseRepository<JobExecution, Long> {
    fun findByJobType(jobType: String): List<JobExecution>

    fun existsByJobTypeAndDedupKeyAndStatusIn(
        jobType: String,
        dedupKey: String,
        statuses: Collection<JobExecutionStatus>
    ): Boolean

    fun findByStatusAndStartedAtBefore(
        status: JobExecutionStatus,
        threshold: Instant
    ): List<JobExecution>

    fun findByStatusAndQueuedAtBefore(
        status: JobExecutionStatus,
        threshold: Instant
    ): List<JobExecution>
}
