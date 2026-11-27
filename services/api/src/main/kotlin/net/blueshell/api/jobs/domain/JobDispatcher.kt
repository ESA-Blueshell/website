package net.blueshell.api.jobs.domain

import net.blueshell.api.jobs.api.JobExecutionService
import net.blueshell.api.jobs.api.JobExecutor
import net.blueshell.api.jobs.persistence.JobExecution
import net.blueshell.api.platform.config.JobQueueProperties
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.JobDefinition
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorProvider
import org.slf4j.LoggerFactory
import org.springframework.core.task.TaskRejectedException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import tools.jackson.databind.ObjectMapper

/**
 * Writes a queued row, then dispatches it after commit or at once outside a transaction.
 * With [JobQueueProperties.autoDispatch] off, rows persist for a caller to execute.
 */
@Service
class JobDispatcher(
    private val objectMapper: ObjectMapper,
    private val jobExecutionService: JobExecutionService,
    private val actorProvider: ActorProvider,
    private val jobExecutor: JobExecutor,
    private val emailJobWorker: EmailJobWorker,
    private val properties: JobQueueProperties,
) : JobQueue {
    private val logger = LoggerFactory.getLogger(JobDispatcher::class.java)

    override fun <T : Any> runAsync(
        job: JobDefinition<T>,
        payload: T,
        trigger: JobTrigger,
        actor: Actor?,
    ): JobExecution? {
        val dedupKey = job.dedupKey(payload)
        return runAsync(job.type, payload, trigger, actor, dedupKey, job.queuesBehindRunning)
    }

    /**
     * Untyped entry point for the operator-facing trigger endpoint, which only knows a job
     * type string. Not on [JobQueue]: domain callers queue a [JobDefinition].
     */
    fun runAsync(
        jobType: String,
        payload: Any?,
        trigger: JobTrigger,
        actor: Actor? = null,
        dedupKey: String? = null,
        queuesBehindRunning: Boolean = false,
        forced: Boolean = false,
    ): JobExecution? {
        val payloadJson = payload?.let { objectMapper.writeValueAsString(it) }
        val resolvedActor = actor ?: actorProvider.currentOrSystem()
        val enqueued =
            jobExecutionService.createQueued(
                jobType = jobType,
                payload = payloadJson,
                actor = resolvedActor,
                trigger = trigger,
                dedupKey = dedupKey,
                queuesBehindRunning = queuesBehindRunning,
                forced = forced,
            ) ?: return null

        if (properties.autoDispatch && enqueued.dispatch) {
            val executionId = enqueued.execution.id!!
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                    object : TransactionSynchronization {
                        override fun afterCommit() {
                            dispatch(enqueued.execution)
                        }
                    },
                )
            } else {
                dispatch(enqueued.execution)
            }
        }
        return enqueued.execution
    }

    /** Routes an already-queued row: email jobs go to the batch worker, all others to the executor. */
    fun dispatch(execution: JobExecution) {
        if (EmailJobs.isEmailJob(execution.jobType)) {
            emailJobWorker.nudge()
            return
        }
        try {
            jobExecutor.executeAsync(execution.id!!)
        } catch (ex: TaskRejectedException) {
            logger.warn("Job execution {} could not be dispatched; it stays QUEUED.", execution.id, ex)
        }
    }
}
