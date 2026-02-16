package net.blueshell.api.platform.integration.job.repository

import net.blueshell.api.platform.integration.job.model.JobExecution
import net.blueshell.api.shared.repository.BaseRepository

interface JobExecutionRepository : BaseRepository<JobExecution, Long> {
    fun findTop200ByOrderByCreatedAtDesc(): List<JobExecution>
    fun findByJobType(jobType: String): List<JobExecution>
}
