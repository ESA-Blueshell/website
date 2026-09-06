package net.blueshell.api.jobs.domain

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.platform.config.JobQueueProperties
import net.blueshell.api.jobs.persistence.JobExecution
import net.blueshell.api.shared.job.JobDefinition
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import net.blueshell.api.jobs.api.JobExecutionService
import net.blueshell.api.jobs.api.JobExecutor

/**
 * Async job dispatcher. Writes a DB row then calls JobExecutor.executeAsync().
 * No message broker required.
 *
 * When [JobQueueProperties.autoDispatch] is false (e.g. in tests), jobs are persisted
 * but not automatically executed. Tests can call [JobExecutor.execute] explicitly.
 */
@Service
class JobDispatcher(
    private val objectMapper: ObjectMapper,
    private val jobExecutionService: JobExecutionService,
    private val actorProvider: ActorProvider,
    private val jobExecutor: JobExecutor,
    private val properties: JobQueueProperties
) : JobQueue {
    override fun <T : Any> runAsync(
        job: JobDefinition<T>,
        payload: T,
        actor: Actor?
    ): JobExecution? {
        val dedupKey = job.dedupKey(payload)
        return runAsync(job.type, payload, actor, dedupKey)
    }

    /**
     * Untyped entry point for the operator-facing trigger endpoint, which only knows a job
     * type string. Not on [JobQueue]: domain callers queue a [JobDefinition].
     */
    fun runAsync(
        jobType: String,
        payload: Any? = null,
        actor: Actor? = null,
        dedupKey: String? = null
    ): JobExecution? {
        val payloadJson = payload?.let { objectMapper.writeValueAsString(it) }
        val resolvedActor = actor ?: actorProvider.currentOrSystem()
        val execution = jobExecutionService.createQueued(
            jobType = jobType,
            payload = payloadJson,
            actor = resolvedActor,
            dedupKey = dedupKey
        ) ?: return null

        if (properties.autoDispatch) {
            val executionId = execution.id!!
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                    override fun afterCommit() {
                        jobExecutor.executeAsync(executionId)
                    }
                })
            } else {
                jobExecutor.executeAsync(executionId)
            }
        }
        return execution
    }
}
