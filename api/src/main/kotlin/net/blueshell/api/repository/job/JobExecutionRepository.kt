package net.blueshell.api.repository.job

import net.blueshell.api.model.job.JobExecution
import org.springframework.data.jpa.repository.JpaRepository

interface JobExecutionRepository : JpaRepository<JobExecution, Long> {
    fun findTop200ByOrderByCreatedAtDesc(): List<JobExecution>
}
