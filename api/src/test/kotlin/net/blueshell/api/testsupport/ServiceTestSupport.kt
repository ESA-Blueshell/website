package net.blueshell.api.testsupport

import jakarta.persistence.EntityManager
import net.blueshell.api.config.TestCleanUpListener
import net.blueshell.api.platform.integration.job.repository.JobExecutionRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestExecutionListeners
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * Base class for service integration tests.
 *
 * Provides:
 * - Full Spring context
 * - Database cleanup between tests
 * - Transaction management
 * - Application event recording
 * - Job execution tracking
 */
@SpringBootTest
@ActiveProfiles("test")
@RecordApplicationEvents
@TestExecutionListeners(
    listeners = [TestCleanUpListener::class],
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS
)
abstract class ServiceTestSupport {
    @Autowired
    protected lateinit var entityManager: EntityManager

    @Autowired
    protected lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    protected lateinit var applicationEvents: ApplicationEvents

    @Autowired
    protected lateinit var jobExecutions: JobExecutionRepository

    protected val transactionTemplate: TransactionTemplate by lazy {
        TransactionTemplate(transactionManager)
    }

    /**
     * Persists entity in separate transaction, then refreshes it.
     * Useful for test setup where you need detached entities.
     */
    protected fun <T> persist(entity: T): T {
        return transactionTemplate.execute {
            val saved = entityManager.merge(entity)
            entityManager.flush()
            entityManager.refresh(saved)
            saved
        }!!
    }

    /**
     * Finds all job executions for a specific job type.
     */
    protected fun findJobsByType(jobType: String) =
        jobExecutions.findByJobType(jobType)
}
