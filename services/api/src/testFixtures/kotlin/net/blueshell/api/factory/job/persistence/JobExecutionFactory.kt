package net.blueshell.api.factory.job.persistence

import net.blueshell.api.factory.support.FactoryPersistenceSupport
import net.blueshell.api.jobs.persistence.JobExecution
import net.blueshell.api.shared.enums.JobExecutionStatus
import org.springframework.stereotype.Component

@Component
class JobExecutionFactory(
    private val persistence: FactoryPersistenceSupport
) {
    fun build(jobType: String = "test-job", status: JobExecutionStatus = JobExecutionStatus.QUEUED): JobExecution {
        return JobExecution(
            jobType = jobType,
            status = status,
            payload = """{"key":"value"}"""
        )
    }

    fun create(jobType: String = "test-job", status: JobExecutionStatus = JobExecutionStatus.QUEUED): JobExecution {
        return persistence.persist(build(jobType, status))
    }
}
