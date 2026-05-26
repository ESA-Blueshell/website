package net.blueshell.api.platform.integration.job.application.service

import jakarta.persistence.EntityManager
import net.blueshell.api.platform.integration.job.persistence.JobExecution
import net.blueshell.api.platform.integration.job.persistence.repository.JobExecutionRepository
import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.shared.tracking.Actor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.lang.reflect.Field

/**
 * Pure unit tests for [JobExecutionService]. No Spring context.
 *
 * Attempt semantics encoded here: `attempts` counts how many times the job has
 * been queued for execution (initial enqueue + each retry / manual requeue),
 * NOT how many times it has finished. So a job that has just been queued for
 * the first time already shows `attempts = 1`, and a job that has run three
 * times shows `attempts = 3`. Pressing the retry button bumps it immediately.
 */
class JobExecutionServiceTest {

    private val repository: JobExecutionRepository = mock()
    private val entityManager: EntityManager = mock()
    private val service = JobExecutionService(repository).also { injectEntityManager(it) }

    private val systemActor = Actor.system()

    @Test
    fun `createQueued initializes attempts to 1 so the initial run counts`() {
        whenever(repository.saveAndFlush(any<JobExecution>())).thenAnswer { it.arguments[0] as JobExecution }

        val execution = service.createQueued(jobType = "demo", payload = null, actor = systemActor)

        assertThat(execution).isNotNull
        assertThat(execution!!.attempts).isEqualTo(1)
        assertThat(execution.status).isEqualTo(JobExecutionStatus.QUEUED)
    }

    @Test
    fun `markRunning does not bump attempts`() {
        val execution = JobExecution(jobType = "demo", attempts = 1, status = JobExecutionStatus.QUEUED)
            .apply { id = 1L }
        stubPersistence(execution)

        service.markRunning(execution)

        assertThat(execution.attempts).isEqualTo(1)
        assertThat(execution.status).isEqualTo(JobExecutionStatus.RUNNING)
    }

    @Test
    fun `markSuccess does not bump attempts`() {
        val execution = JobExecution(jobType = "demo", attempts = 1, status = JobExecutionStatus.RUNNING)
            .apply { id = 1L }
        stubPersistence(execution)

        service.markSuccess(execution)

        assertThat(execution.attempts).isEqualTo(1)
        assertThat(execution.status).isEqualTo(JobExecutionStatus.SUCCESS)
    }

    @Test
    fun `markFailed does not bump attempts`() {
        val execution = JobExecution(jobType = "demo", attempts = 3, status = JobExecutionStatus.RUNNING)
            .apply { id = 1L }
        stubPersistence(execution)

        service.markFailed(execution, "SomeError", "boom")

        assertThat(execution.attempts).isEqualTo(3)
        assertThat(execution.status).isEqualTo(JobExecutionStatus.FAILED)
    }

    @Test
    fun `markDead does not bump attempts`() {
        val execution = JobExecution(jobType = "demo", attempts = 1, status = JobExecutionStatus.RUNNING)
            .apply { id = 1L }
        stubPersistence(execution)

        service.markDead(execution, "SomeError", "boom")

        assertThat(execution.attempts).isEqualTo(1)
        assertThat(execution.status).isEqualTo(JobExecutionStatus.DEAD)
    }

    @Test
    fun `markRetryScheduled bumps attempts so the upcoming retry is counted`() {
        val execution = JobExecution(jobType = "demo", attempts = 1, status = JobExecutionStatus.RUNNING)
            .apply { id = 1L }
        stubPersistence(execution)

        service.markRetryScheduled(execution, "SomeError", "boom", nextAttemptAt = java.time.Instant.now())

        assertThat(execution.attempts).isEqualTo(2)
        assertThat(execution.status).isEqualTo(JobExecutionStatus.QUEUED)
    }

    @Test
    fun `requeue bumps attempts immediately so the count reflects the upcoming run`() {
        val execution = JobExecution(jobType = "demo", attempts = 3, status = JobExecutionStatus.FAILED)
            .apply { id = 7L }
        stubPersistence(execution)

        val result = service.requeue(execution)

        assertThat(result.attempts).isEqualTo(4)
        assertThat(result.status).isEqualTo(JobExecutionStatus.QUEUED)
        assertThat(result.nextAttemptAt).isNull()
    }

    @Test
    fun `requeue increments attempts on each successive call`() {
        val execution = JobExecution(jobType = "demo", attempts = 1, status = JobExecutionStatus.FAILED)
            .apply { id = 7L }
        stubPersistence(execution)

        service.requeue(execution)
        execution.status = JobExecutionStatus.FAILED // simulate the next failure
        service.requeue(execution)
        execution.status = JobExecutionStatus.FAILED
        service.requeue(execution)

        assertThat(execution.attempts).isEqualTo(4)
    }

    /**
     * Walks the user's stated scenario: a job that runs three times (initial +
     * two retries) and succeeds on the third should end at attempts == 3.
     */
    @Test
    fun `one successful run leaves attempts at 1; three successful runs at 3`() {
        whenever(repository.saveAndFlush(any<JobExecution>())).thenAnswer { it.arguments[0] as JobExecution }

        // One run → 1 attempt
        val once = service.createQueued("demo", null, systemActor)!!
            .also { it.id = 11L; whenever(repository.existsById(it.id!!)).thenReturn(true) }
        service.markRunning(once)
        service.markSuccess(once)
        assertThat(once.attempts).describedAs("single successful run").isEqualTo(1)

        // Three runs (fail, fail, succeed) → 3 attempts
        val thrice = service.createQueued("demo", null, systemActor)!!
            .also { it.id = 12L; whenever(repository.existsById(it.id!!)).thenReturn(true) }
        service.markRunning(thrice)
        service.markRetryScheduled(thrice, "E", "r", nextAttemptAt = java.time.Instant.now())
        service.markRunning(thrice)
        service.markRetryScheduled(thrice, "E", "r", nextAttemptAt = java.time.Instant.now())
        service.markRunning(thrice)
        service.markSuccess(thrice)
        assertThat(thrice.attempts).describedAs("three successive runs").isEqualTo(3)

        // Then admin clicks retry: attempts goes to 4 immediately
        service.requeue(thrice)
        assertThat(thrice.attempts).describedAs("attempts after retry click").isEqualTo(4)
    }

    private fun stubPersistence(entity: JobExecution) {
        whenever(repository.existsById(entity.id!!)).thenReturn(true)
        whenever(repository.saveAndFlush(any<JobExecution>())).thenAnswer { it.arguments[0] }
    }

    private fun injectEntityManager(service: JobExecutionService) {
        val field: Field = service.javaClass.superclass.getDeclaredField("em")
        field.isAccessible = true
        field.set(service, entityManager)
    }
}
