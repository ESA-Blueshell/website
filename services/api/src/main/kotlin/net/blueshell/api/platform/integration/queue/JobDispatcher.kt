package net.blueshell.api.platform.integration.queue

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.platform.config.JobQueueProperties
import net.blueshell.api.platform.integration.job.persistence.JobExecution
import net.blueshell.api.platform.integration.job.application.service.JobExecutionService
import net.blueshell.api.shared.job.JobDefinition
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

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
    override fun <T : Any> enqueue(
        job: JobDefinition<T>,
        payload: T,
        actor: Actor?
    ): JobExecution? {
        val dedupKey = job.dedupKey(payload)
        return enqueueInternal(
            jobType = job.type,
            payload = payload,
            actor = actor,
            dedupKey = dedupKey,
            coalesceAgainstQueuedOnly = job.coalesceAgainstQueuedOnly
        )
    }

    override fun enqueue(
        jobType: String,
        payload: Any?,
        actor: Actor?,
        dedupKey: String?
    ): JobExecution? = enqueueInternal(
        jobType = jobType,
        payload = payload,
        actor = actor,
        dedupKey = dedupKey,
        coalesceAgainstQueuedOnly = false
    )

    private fun enqueueInternal(
        jobType: String,
        payload: Any?,
        actor: Actor?,
        dedupKey: String?,
        coalesceAgainstQueuedOnly: Boolean
    ): JobExecution? {
        val payloadJson = payload?.let { objectMapper.writeValueAsString(it) }
        val resolvedActor = actor ?: actorProvider.currentOrSystem()
        val execution = jobExecutionService.createQueued(
            jobType = jobType,
            payload = payloadJson,
            actor = resolvedActor,
            dedupKey = dedupKey,
            coalesceAgainstQueuedOnly = coalesceAgainstQueuedOnly
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
