package net.blueshell.api.jobs.domain

import jakarta.persistence.EntityManager
import net.blueshell.api.jobs.api.JobExecutionService
import net.blueshell.api.jobs.persistence.ActiveTwin
import net.blueshell.api.jobs.persistence.FoldedTrigger
import net.blueshell.api.jobs.persistence.JobEnqueueLocks
import net.blueshell.api.jobs.persistence.JobExecution
import net.blueshell.api.jobs.persistence.JobExecutionRepository
import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.JobEffect
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.shared.tracking.Actor
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.lang.reflect.Field
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

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
    private val locks: JobEnqueueLocks = mock()
    private val entityManager: EntityManager = mock()
    private val now = Instant.parse("2026-10-01T10:00:00Z")
    private val service =
        JobExecutionService(repository, locks).also {
            injectEntityManager(it)
            it.clock = Clock.fixed(now, ZoneOffset.UTC)
        }

    private val systemActor = Actor.system()
    private val board = Actor.user(5, Role.BOARD)

    /** The twins of `demo` with dedup key `k` as committed now, the lock taken. */
    private fun twins(vararg found: JobExecution) {
        whenever(locks.lock("demo", "k", 10)).thenReturn(true)
        whenever(locks.activeTwins("demo", "k")).thenReturn(found.map { ActiveTwin(it.id!!, it.status) })
        found.forEach {
            whenever(repository.existsById(it.id!!)).thenReturn(true)
            whenever(repository.findByIdForUpdate(it.id!!)).thenReturn(it)
        }
        whenever(repository.saveAndFlush(any<JobExecution>())).thenAnswer { it.arguments[0] as JobExecution }
    }

    private fun twin(
        status: JobExecutionStatus,
        nextAttemptAt: Instant? = null,
    ) = JobExecution(
        jobType = "demo",
        status = status,
        dedupKey = "k",
        queuedAt = now.minusSeconds(5),
        nextAttemptAt = nextAttemptAt,
    ).apply { id = 3 }

    @Test
    fun `createQueued drops a job behind a running twin, or queues it behind one where its definition asks`() {
        twins(twin(JobExecutionStatus.RUNNING))

        assertThat(service.createQueued("demo", null, systemActor, dedupKey = "k")).isNull()
        val behind = service.createQueued("demo", null, systemActor, dedupKey = "k", queuesBehindRunning = true)!!
        assertThat(behind.dispatch).isTrue()
        assertThat(behind.execution.id).isNull()
    }

    @Test
    fun `a trigger that meets a twin waiting out its backoff pulls it forward and is noted on it`() {
        val waiting = twin(JobExecutionStatus.QUEUED, nextAttemptAt = now.plusSeconds(600))
        twins(waiting)

        val enqueued = service.createQueued("demo", null, board, JobTrigger.EVENT_UPDATED, dedupKey = "k", queuesBehindRunning = true)!!

        assertThat(enqueued.execution).isSameAs(waiting)
        assertThat(enqueued.dispatch).isTrue()
        assertThat(waiting.nextAttemptAt).isNull()
        assertThat(waiting.queuedAt).isEqualTo(now)
        assertThat(waiting.foldedTriggers).containsExactly(FoldedTrigger(JobTrigger.EVENT_UPDATED, board, now))
    }

    @Test
    fun `a trigger that meets a twin about to run leaves its timing alone and is noted on it`() {
        val due = twin(JobExecutionStatus.QUEUED)
        twins(due)

        val enqueued = service.createQueued("demo", null, board, JobTrigger.SIGN_UPS_CHANGED, dedupKey = "k")!!
        service.createQueued("demo", null, board, dedupKey = "k")

        assertThat(enqueued.execution).isSameAs(due)
        assertThat(enqueued.dispatch).isFalse()
        assertThat(due.queuedAt).isEqualTo(now.minusSeconds(5))
        assertThat(due.foldedTriggers.map { it.trigger }).containsExactly(JobTrigger.SIGN_UPS_CHANGED)
    }

    @Test
    fun `takes the lock for the job type and key before reading its twins, and gives up where another keeps it`() {
        twins()

        service.createQueued("demo", null, systemActor, dedupKey = "k")

        inOrder(locks) {
            verify(locks).lock("demo", "k", 10)
            verify(locks).activeTwins("demo", "k")
        }
        whenever(locks.lock("demo", "k", 10)).thenReturn(false)
        assertThatThrownBy { service.createQueued("demo", null, systemActor, dedupKey = "k") }.hasMessageContaining("enqueue")
    }

    @Test
    fun `a queued twin that started since it was read counts as running`() {
        val started = twin(JobExecutionStatus.QUEUED)
        twins(started)
        whenever(repository.findByIdForUpdate(3)).thenReturn(twin(JobExecutionStatus.RUNNING))

        assertThat(service.createQueued("demo", null, systemActor, dedupKey = "k")).isNull()
        assertThat(service.createQueued("demo", null, systemActor, dedupKey = "k", queuesBehindRunning = true)!!.dispatch).isTrue()
    }

    @Test
    fun `createQueued initializes attempts to 1 so the initial run counts`() {
        whenever(repository.saveAndFlush(any<JobExecution>())).thenAnswer { it.arguments[0] as JobExecution }

        val execution = service.createQueued(jobType = "demo", payload = null, actor = systemActor)?.execution

        assertThat(execution).isNotNull
        assertThat(execution!!.attempts).isEqualTo(1)
        assertThat(execution.status).isEqualTo(JobExecutionStatus.QUEUED)
    }

    @Test
    fun `markRunning does not bump attempts`() {
        val execution =
            JobExecution(jobType = "demo", attempts = 1, status = JobExecutionStatus.QUEUED)
                .apply { id = 1L }
        stubPersistence(execution)

        service.markRunning(execution)

        assertThat(execution.attempts).isEqualTo(1)
        assertThat(execution.status).isEqualTo(JobExecutionStatus.RUNNING)
    }

    @Test
    fun `markSuccess does not bump attempts`() {
        val execution =
            JobExecution(jobType = "demo", attempts = 1, status = JobExecutionStatus.RUNNING)
                .apply { id = 1L }
        stubPersistence(execution)

        service.markSuccess(execution)

        assertThat(execution.attempts).isEqualTo(1)
        assertThat(execution.status).isEqualTo(JobExecutionStatus.SUCCESS)
    }

    @Test
    fun `markFailed does not bump attempts`() {
        val execution =
            JobExecution(jobType = "demo", attempts = 3, status = JobExecutionStatus.RUNNING)
                .apply { id = 1L }
        stubPersistence(execution)

        service.markFailed(execution, "SomeError", "boom")

        assertThat(execution.attempts).isEqualTo(3)
        assertThat(execution.status).isEqualTo(JobExecutionStatus.FAILED)
    }

    @Test
    fun `markDead does not bump attempts`() {
        val execution =
            JobExecution(jobType = "demo", attempts = 1, status = JobExecutionStatus.RUNNING)
                .apply { id = 1L }
        stubPersistence(execution)

        service.markDead(execution, "SomeError", "boom")

        assertThat(execution.attempts).isEqualTo(1)
        assertThat(execution.status).isEqualTo(JobExecutionStatus.DEAD)
    }

    @Test
    fun `markRetryScheduled bumps attempts so the upcoming retry is counted`() {
        val execution =
            JobExecution(jobType = "demo", attempts = 1, status = JobExecutionStatus.RUNNING)
                .apply { id = 1L }
        stubPersistence(execution)

        service.markRetryScheduled(execution, "SomeError", "boom", nextAttemptAt = java.time.Instant.now())

        assertThat(execution.attempts).isEqualTo(2)
        assertThat(execution.status).isEqualTo(JobExecutionStatus.QUEUED)
    }

    @Test
    fun `records an explained failure as its sentence alone, and any other as its type and message`() {
        val explained = JobExecution(jobType = "demo", status = JobExecutionStatus.RUNNING).apply { id = 1L }
        val plain = JobExecution(jobType = "demo", status = JobExecutionStatus.RUNNING).apply { id = 2L }
        stubPersistence(explained)
        stubPersistence(plain)

        service.markRetryScheduled(explained, "Refused", "Discord is unavailable.", "trace", java.time.Instant.now(), explained = true)
        service.markFailed(plain, "SomeError", "boom", "trace")

        assertThat(explained.errorMessage).isEqualTo("Discord is unavailable.")
        assertThat(explained.errorReason).isEqualTo("trace")
        assertThat(plain.errorMessage).isEqualTo("SomeError: boom")
    }

    @Test
    fun `requeue bumps attempts immediately so the count reflects the upcoming run`() {
        val execution =
            JobExecution(jobType = "demo", attempts = 3, status = JobExecutionStatus.FAILED)
                .apply { id = 7L }
        stubPersistence(execution)

        val result = service.requeue(execution)

        assertThat(result.attempts).isEqualTo(4)
        assertThat(result.status).isEqualTo(JobExecutionStatus.QUEUED)
        assertThat(result.nextAttemptAt).isNull()
    }

    @Test
    fun `requeue forces the run and forgets why the last one skipped`() {
        val execution =
            JobExecution(jobType = "demo", attempts = 1, status = JobExecutionStatus.SKIPPED, skipReason = "Not due yet.")
                .apply { id = 7L }
        stubPersistence(execution)

        val result = service.requeue(execution)

        assertThat(result.forced).isTrue()
        assertThat(result.skipReason).isNull()
    }

    @Test
    fun `markSkipped finishes the run with its reason`() {
        val execution =
            JobExecution(jobType = "demo", attempts = 1, status = JobExecutionStatus.RUNNING)
                .apply { id = 7L }
        stubPersistence(execution)

        val result = service.markSkipped(execution, "Not due yet.")

        assertThat(result.status).isEqualTo(JobExecutionStatus.SKIPPED)
        assertThat(result.skipReason).isEqualTo("Not due yet.")
        assertThat(result.finishedAt).isNotNull()
    }

    @Test
    fun `a success records what the run did, and the next run starts without it`() {
        whenever(repository.existsById(any())).thenReturn(true)
        whenever(repository.saveAndFlush(any<JobExecution>())).thenAnswer { it.arguments[0] as JobExecution }
        val execution = JobExecution(jobType = "demo").apply { id = 1 }

        service.markSuccess(execution, JobEffect.MADE, "https://discord.test/m1")
        assertThat(execution.effect to execution.effectLink).isEqualTo(JobEffect.MADE to "https://discord.test/m1")

        service.markRunning(execution)
        assertThat(execution.effect to execution.effectLink).isEqualTo(null to null)
    }

    @Test
    fun `createQueued records what queued the job`() {
        whenever(repository.saveAndFlush(any<JobExecution>())).thenAnswer { it.arguments[0] as JobExecution }

        assertThat(service.createQueued("demo", null, systemActor, trigger = JobTrigger.MORNING_RUN)!!.execution.trigger)
            .isEqualTo(JobTrigger.MORNING_RUN)
        assertThat(service.createQueued("demo", null, systemActor)!!.execution.trigger).isNull()
    }

    @Test
    fun `createQueued records a run asked for by hand`() {
        whenever(repository.saveAndFlush(any<JobExecution>())).thenAnswer { it.arguments[0] as JobExecution }

        assertThat(service.createQueued("demo", null, systemActor, forced = true)!!.execution.forced).isTrue()
        assertThat(service.createQueued("demo", null, systemActor)!!.execution.forced).isFalse()
    }

    @Test
    fun `requeue increments attempts on each successive call`() {
        val execution =
            JobExecution(jobType = "demo", attempts = 1, status = JobExecutionStatus.FAILED)
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
    fun `one successful run leaves attempts at 1 and three successful runs at 3`() {
        whenever(repository.saveAndFlush(any<JobExecution>())).thenAnswer { it.arguments[0] as JobExecution }

        // One run → 1 attempt
        val once =
            service
                .createQueued("demo", null, systemActor)!!
                .execution
                .also {
                    it.id = 11L
                    whenever(repository.existsById(it.id!!)).thenReturn(true)
                }
        service.markRunning(once)
        service.markSuccess(once)
        assertThat(once.attempts).describedAs("single successful run").isEqualTo(1)

        // Three runs (fail, fail, succeed) → 3 attempts
        val thrice =
            service
                .createQueued("demo", null, systemActor)!!
                .execution
                .also {
                    it.id = 12L
                    whenever(repository.existsById(it.id!!)).thenReturn(true)
                }
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

    @Test
    fun `retryWithSupersede marks same-kind siblings dead and requeues the target`() {
        val target = jobExecution(100L, dedupKey = "k", status = JobExecutionStatus.FAILED, attempts = 3)
        val failedSibling = jobExecution(101L, dedupKey = "k", status = JobExecutionStatus.FAILED)
        val succeededSibling = jobExecution(102L, dedupKey = "k", status = JobExecutionStatus.SUCCESS)
        val alreadyDead = jobExecution(103L, dedupKey = "k", status = JobExecutionStatus.DEAD)
        whenever(repository.findByJobTypeAndDedupKey("contact.sync", "k"))
            .thenReturn(listOf(target, failedSibling, succeededSibling, alreadyDead))
        whenever(repository.existsById(any())).thenReturn(true)
        whenever(repository.saveAndFlush(any<JobExecution>())).thenAnswer { it.arguments[0] }

        val result = service.retryWithSupersede(target)

        assertThat(result.id).isEqualTo(100L)
        assertThat(result.status).isEqualTo(JobExecutionStatus.QUEUED)
        assertThat(result.attempts).isEqualTo(4)
        assertThat(failedSibling.status).isEqualTo(JobExecutionStatus.DEAD)
        assertThat(succeededSibling.status).isEqualTo(JobExecutionStatus.DEAD)
        assertThat(alreadyDead.errorMessage).describedAs("already-dead sibling left untouched").isNull()
    }

    @Test
    fun `retryWithSupersede matches siblings by payload when dedupKey is null`() {
        val target = jobExecution(1L, dedupKey = null, payload = """{"userId":9}""", status = JobExecutionStatus.DEAD)
        val sibling = jobExecution(2L, dedupKey = null, payload = """{"userId":9}""", status = JobExecutionStatus.FAILED)
        whenever(repository.findByJobTypeAndPayload("contact.sync", """{"userId":9}"""))
            .thenReturn(listOf(target, sibling))
        whenever(repository.existsById(any())).thenReturn(true)
        whenever(repository.saveAndFlush(any<JobExecution>())).thenAnswer { it.arguments[0] }

        service.retryWithSupersede(target)

        assertThat(sibling.status).isEqualTo(JobExecutionStatus.DEAD)
        assertThat(target.status).isEqualTo(JobExecutionStatus.QUEUED)
    }

    private fun jobExecution(
        id: Long,
        dedupKey: String?,
        status: JobExecutionStatus,
        attempts: Int = 1,
        payload: String? = """{"userId":1}""",
    ): JobExecution =
        JobExecution(
            jobType = "contact.sync",
            status = status,
            payload = payload,
            attempts = attempts,
            dedupKey = dedupKey,
        ).apply { this.id = id }

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
