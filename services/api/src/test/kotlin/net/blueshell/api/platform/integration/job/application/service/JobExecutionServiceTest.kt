package net.blueshell.api.platform.integration.job.application.service

import jakarta.persistence.EntityManager
import net.blueshell.api.platform.integration.job.persistence.JobExecution
import net.blueshell.api.platform.integration.job.persistence.repository.JobExecutionRepository
import net.blueshell.api.shared.enums.JobExecutionStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.lang.reflect.Field

/**
 * Pure unit tests for [JobExecutionService]. No Spring context.
 *
 * The base [net.blueshell.api.shared.service.BaseModelService] is a JPA-aware
 * helper that uses an injected [EntityManager]; we inject a mock so `update`
 * works against the mocked repository.
 */
class JobExecutionServiceTest {

    private val repository: JobExecutionRepository = mock()
    private val entityManager: EntityManager = mock()
    private val service = JobExecutionService(repository).also { injectEntityManager(it) }

    @Test
    fun `requeue increments attempts from any starting value`() {
        // Simulate a job that already failed four times (display would show "5 attempts" via +1).
        val execution = JobExecution(jobType = "demo", attempts = 4, status = JobExecutionStatus.FAILED)
            .apply { id = 7L }
        stubPersistence(execution)

        val result = service.requeue(execution)

        assertThat(result.attempts).isEqualTo(5)
        assertThat(result.status).isEqualTo(JobExecutionStatus.QUEUED)
        assertThat(result.nextAttemptAt).isNull()
    }

    @Test
    fun `requeue increments attempts on each successive call`() {
        val execution = JobExecution(jobType = "demo", attempts = 0, status = JobExecutionStatus.FAILED)
            .apply { id = 7L }
        stubPersistence(execution)

        service.requeue(execution)
        execution.status = JobExecutionStatus.FAILED // simulate the next failure
        service.requeue(execution)
        execution.status = JobExecutionStatus.FAILED
        service.requeue(execution)

        assertThat(execution.attempts).isEqualTo(3)
    }

    /**
     * saveAndFlush returns the persisted entity; the BaseModelService then
     * calls em.refresh on the returned reference. The mock simply echoes the
     * entity back so we can observe the field mutations.
     */
    private fun stubPersistence(entity: JobExecution) {
        whenever(repository.existsById(entity.id!!)).thenReturn(true)
        whenever(repository.saveAndFlush(any<JobExecution>())).thenAnswer { it.arguments[0] }
    }

    private fun injectEntityManager(service: JobExecutionService) {
        // BaseModelService.em is package-private lateinit; reflect to set the mock.
        val field: Field = service.javaClass.superclass.getDeclaredField("em")
        field.isAccessible = true
        field.set(service, entityManager)
    }
}
