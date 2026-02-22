package net.blueshell.api.factory.job.persistence

import net.blueshell.api.factory.support.FactoryPersistenceSupport
import net.blueshell.api.platform.integration.job.persistence.JobExecution
import net.blueshell.api.shared.enums.JobExecutionStatus
import org.springframework.stereotype.Component

@Component
class JobExecutionFactory(
    private val persistence: FactoryPersistenceSupport
) {
    fun build(jobType: String = "test-job"): JobExecution {
        return JobExecution(
            jobType = jobType,
            status = JobExecutionStatus.QUEUED,
            payload = """{"key":"value"}"""
        )
    }

    fun create(jobType: String = "test-job"): JobExecution {
        return persistence.persist(build(jobType))
    }
}
