package net.blueshell.api.platform.integration.job.repository

import net.blueshell.api.platform.integration.job.model.JobExecution
import org.springframework.data.jpa.repository.JpaRepository

interface JobExecutionRepository : JpaRepository<JobExecution, Long> {
    fun findTop200ByOrderByCreatedAtDesc(): List<JobExecution>
    fun findByJobType(jobType: String): List<JobExecution>
}
