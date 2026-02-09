package net.blueshell.api.testsupport

import jakarta.persistence.EntityManager
import net.blueshell.api.platform.config.TruncateTestDatabaseListener
import net.blueshell.api.platform.integration.job.repository.JobExecutionRepository
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.context.event.RecordApplicationEvents
import org.springframework.boot.test.context.event.ApplicationEvents
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestExecutionListeners
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@ActiveProfiles("test")
@RecordApplicationEvents
@TestExecutionListeners(
    listeners = [TruncateTestDatabaseListener::class],
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS
)
abstract class EventIntegrationTestSupport {
    @Autowired
    protected lateinit var entityManager: EntityManager

    @Autowired
    protected lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    protected lateinit var applicationEvents: ApplicationEvents

    @Autowired
    protected lateinit var jobExecutions: JobExecutionRepository

    @MockBean
    protected lateinit var rabbitTemplate: RabbitTemplate

    protected val transactionTemplate: TransactionTemplate by lazy {
        TransactionTemplate(transactionManager)
    }

    protected fun <T> persist(entity: T): T {
        return transactionTemplate.execute {
            val saved = entityManager.merge(entity)
            entityManager.flush()
            entityManager.refresh(saved)
            saved
        }!!
    }
}
