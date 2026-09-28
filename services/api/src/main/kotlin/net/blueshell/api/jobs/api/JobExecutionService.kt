package net.blueshell.api.jobs.api

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import net.blueshell.api.jobs.domain.JobExecutionQuery
import net.blueshell.api.jobs.persistence.FoldedTrigger
import net.blueshell.api.jobs.persistence.JobEnqueueLocks
import net.blueshell.api.jobs.persistence.JobExecution
import net.blueshell.api.jobs.persistence.JobExecutionRepository
import net.blueshell.api.jobs.persistence.JobExecutionSpecifications
import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.shared.job.JobEffect
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.shared.tracking.Actor
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Instant

/** What an enqueue came to: a new row, or the queued twin it was folded into, and whether that is to be dispatched now. */
data class Enqueued(
    val execution: JobExecution,
    val dispatch: Boolean,
)

// One mark per status an execution can reach.
@Suppress("TooManyFunctions")
@Service
class JobExecutionService(
    private val jobExecutionRepository: JobExecutionRepository,
    private val enqueueLocks: JobEnqueueLocks,
) {
    // Read back after each write, so the columns the database fills are on the answer.
    @PersistenceContext
    private lateinit var em: EntityManager

    private fun written(row: JobExecution): JobExecution = jobExecutionRepository.saveAndFlush(row).also(em::refresh)

    // The existence query flushes the session first, which writes what the edit cascades before
    // the merge; merging it unwritten fails on a lazy owner.
    private fun rewritten(row: JobExecution): JobExecution {
        val id = row.id
        if (id == null || !jobExecutionRepository.existsById(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "JobExecution not found with id: $id")
        }
        return written(row)
    }

    @Transactional(readOnly = true)
    fun findById(id: Long): JobExecution =
        jobExecutionRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "JobExecution not found with id: $id")
        }

    // Settable for tests only.
    internal var clock: Clock = Clock.systemUTC()

    /**
     * Queues a job, or folds it into its queued twin: one of the same type and dedup key, which
     * reads what it works on only once it runs. A twin waiting out a retry's backoff is pulled
     * forward to run now. Null where a running twin makes the job redundant, which it does not
     * for a job that [queuesBehindRunning].
     */
    @Transactional
    fun createQueued(
        jobType: String,
        payload: String?,
        actor: Actor,
        trigger: JobTrigger? = null,
        dedupKey: String? = null,
        queuesBehindRunning: Boolean = false,
        forced: Boolean = false,
    ): Enqueued? {
        val create = { Enqueued(written(newExecution(jobType, payload, actor, trigger, dedupKey, forced)), dispatch = true) }
        if (dedupKey == null) return create()
        check(enqueueLocks.lock(jobType, dedupKey, ENQUEUE_LOCK_SECONDS)) { "Another enqueue of $jobType kept its lock" }
        val twins = enqueueLocks.activeTwins(jobType, dedupKey)
        // It may have started since it was read, and then it is a running twin.
        val queued =
            twins
                .firstOrNull { it.status == JobExecutionStatus.QUEUED }
                ?.let { jobExecutionRepository.findByIdForUpdate(it.id) }
                ?.takeIf { it.status == JobExecutionStatus.QUEUED }
        return when {
            queued != null -> fold(queued, trigger, actor)
            twins.isNotEmpty() && !queuesBehindRunning -> null
            else -> create()
        }
    }

    private fun fold(
        twin: JobExecution,
        trigger: JobTrigger?,
        actor: Actor,
    ): Enqueued {
        val now = clock.instant()
        val waiting = twin.nextAttemptAt?.isAfter(now) == true
        if (trigger != null) twin.foldedTriggers += FoldedTrigger(trigger, actor, now)
        if (waiting) {
            // A fresh row's shape: dispatched after this commit, and left alone by both sweeps meanwhile.
            twin.nextAttemptAt = null
            twin.queuedAt = now
        }
        return Enqueued(rewritten(twin), dispatch = waiting)
    }

    private fun newExecution(
        jobType: String,
        payload: String?,
        actor: Actor,
        trigger: JobTrigger?,
        dedupKey: String?,
        forced: Boolean,
    ): JobExecution =
        JobExecution(
            jobType = jobType,
            status = JobExecutionStatus.QUEUED,
            payload = payload,
            // The initial enqueue already counts: attempts represents the
            // upcoming-or-current run number, so a job that hasn't started
            // yet shows attempts = 1 in the UI rather than 0.
            attempts = 1,
            queuedAt = clock.instant(),
            dedupKey = dedupKey,
            forced = forced,
            trigger = trigger,
            initiatedByUserId = actor.userId,
            initiatedByType = actor.type,
            initiatedByRole = actor.role,
        )

    @Transactional(readOnly = true)
    fun findByFilter(
        pageable: Pageable,
        filter: JobExecutionQuery,
    ): Page<JobExecution> {
        val spec = JobExecutionSpecifications.fromFilter(filter)
        return jobExecutionRepository.findAll(spec, pageable)
    }

    @Transactional(readOnly = true)
    fun findByIdOrNull(id: Long): JobExecution? = jobExecutionRepository.findById(id).orElse(null)

    @Transactional(readOnly = true)
    fun countAllByStatus(): Map<JobExecutionStatus, Long> =
        JobExecutionStatus.entries.associateWith { jobExecutionRepository.countByStatus(it) }

    @Transactional(readOnly = true)
    fun findStaleRunning(
        threshold: Instant,
        pageable: Pageable,
    ): List<JobExecution> = jobExecutionRepository.findByStatusAndStartedAtBefore(JobExecutionStatus.RUNNING, threshold, pageable)

    @Transactional(readOnly = true)
    fun findStaleQueued(
        threshold: Instant,
        pageable: Pageable,
    ): List<JobExecution> =
        jobExecutionRepository.findByStatusAndNextAttemptAtIsNullAndQueuedAtBefore(
            JobExecutionStatus.QUEUED,
            threshold,
            pageable,
        )

    @Transactional(readOnly = true)
    fun findDueScheduledRetries(
        now: Instant,
        pageable: Pageable,
    ): List<JobExecution> =
        jobExecutionRepository.findByStatusAndNextAttemptAtLessThanEqual(
            JobExecutionStatus.QUEUED,
            now,
            pageable,
        )

    @Transactional
    fun resetRunningToQueued(execution: JobExecution): JobExecution {
        execution.status = JobExecutionStatus.QUEUED
        execution.startedAt = null
        execution.queuedAt = Instant.now()
        return rewritten(execution)
    }

    @Transactional
    fun markRunning(execution: JobExecution): JobExecution {
        execution.status = JobExecutionStatus.RUNNING
        execution.startedAt = Instant.now()
        execution.effect = null
        execution.effectLink = null
        return rewritten(execution)
    }

    @Transactional
    fun markSuccess(
        execution: JobExecution,
        effect: JobEffect? = null,
        link: String? = null,
    ): JobExecution {
        execution.status = JobExecutionStatus.SUCCESS
        execution.finishedAt = Instant.now()
        execution.effect = effect
        execution.effectLink = link
        execution.errorMessage = null
        execution.errorType = null
        execution.errorReason = null
        return rewritten(execution)
    }

    @Transactional
    fun markSkipped(
        execution: JobExecution,
        reason: String,
    ): JobExecution {
        execution.status = JobExecutionStatus.SKIPPED
        execution.finishedAt = Instant.now()
        execution.skipReason = reason
        execution.errorMessage = null
        execution.errorType = null
        execution.errorReason = null
        return rewritten(execution)
    }

    @Transactional
    fun markFailed(
        execution: JobExecution,
        errorType: String,
        errorReason: String,
        stackTrace: String? = null,
        explained: Boolean = false,
    ): JobExecution {
        execution.status = JobExecutionStatus.FAILED
        execution.finishedAt = Instant.now()
        applyErrorInfo(execution, errorType, errorReason, stackTrace, explained)
        return rewritten(execution)
    }

    @Transactional
    fun markDead(
        execution: JobExecution,
        errorType: String,
        errorReason: String,
        stackTrace: String? = null,
    ): JobExecution {
        execution.status = JobExecutionStatus.DEAD
        execution.finishedAt = Instant.now()
        applyErrorInfo(execution, errorType, errorReason, stackTrace)
        return rewritten(execution)
    }

    @Transactional
    fun markRetryScheduled(
        execution: JobExecution,
        errorType: String,
        errorReason: String,
        stackTrace: String? = null,
        nextAttemptAt: Instant,
        explained: Boolean = false,
    ): JobExecution {
        execution.status = JobExecutionStatus.QUEUED
        execution.queuedAt = Instant.now()
        execution.startedAt = null
        execution.finishedAt = null
        execution.nextAttemptAt = nextAttemptAt
        applyErrorInfo(execution, errorType, errorReason, stackTrace, explained)
        execution.attempts += 1
        return rewritten(execution)
    }

    // An explained failure's reason is already a sentence for the jobs page, which its type would only clutter.
    private fun applyErrorInfo(
        execution: JobExecution,
        errorType: String,
        errorReason: String,
        stackTrace: String?,
        explained: Boolean = false,
    ) {
        execution.errorType = errorType
        execution.errorReason = stackTrace?.takeIf { it.isNotBlank() } ?: errorReason
        execution.errorMessage = if (explained) errorReason else "$errorType: $errorReason"
    }

    /**
     * Manual retry triggered from the admin UI. Marks every other execution of
     * the same kind and arguments DEAD (so the list collapses to one canonical
     * job), then requeues this one. The requeue bumps `updatedAt`, so with the
     * list ordered by most-recent activity the retried job jumps to the top.
     */
    @Transactional
    fun retryWithSupersede(execution: JobExecution): JobExecution {
        supersedeSiblings(execution)
        return requeue(execution)
    }

    private fun supersedeSiblings(execution: JobExecution) {
        val siblings =
            when {
                execution.dedupKey != null ->
                    jobExecutionRepository.findByJobTypeAndDedupKey(execution.jobType, execution.dedupKey!!)
                execution.payload != null ->
                    jobExecutionRepository.findByJobTypeAndPayload(execution.jobType, execution.payload!!)
                else -> emptyList()
            }
        siblings
            .filter { it.id != execution.id && it.status != JobExecutionStatus.DEAD }
            .forEach {
                markDead(it, "SupersededByRetry", "Superseded by manual retry of job ${execution.id}")
            }
    }

    /**
     * Manual retry triggered from the admin UI. Preserves the attempt count
     * (incrementing it) and clears any pending retry schedule so the job runs
     * immediately. Being asked for by hand, the run is forced.
     */
    @Transactional
    fun requeue(execution: JobExecution): JobExecution {
        execution.status = JobExecutionStatus.QUEUED
        execution.queuedAt = Instant.now()
        execution.startedAt = null
        execution.finishedAt = null
        execution.nextAttemptAt = null
        execution.errorMessage = null
        execution.errorType = null
        execution.errorReason = null
        execution.skipReason = null
        execution.forced = true
        execution.attempts += 1
        return rewritten(execution)
    }

    private companion object {
        const val ENQUEUE_LOCK_SECONDS = 10
    }
}
