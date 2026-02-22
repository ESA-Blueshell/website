package net.blueshell.api.platform.integration.queue

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.platform.config.JobQueueProperties
import net.blueshell.api.platform.integration.job.model.JobExecution
import net.blueshell.api.platform.integration.job.service.JobExecutionService
import net.blueshell.api.shared.job.JobDefinition
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorProvider
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Service

/**
 * RabbitMQ-based implementation of JobQueue.
 * Domain services depend on the JobQueue interface, not this implementation.
 */
@Service
class JobDispatcher(
    private val rabbitTemplate: RabbitTemplate,
    private val objectMapper: ObjectMapper,
    private val jobQueueProperties: JobQueueProperties,
    private val jobExecutionService: JobExecutionService,
    private val actorProvider: ActorProvider
) : JobQueue {
    override fun <T : Any> enqueue(
        job: JobDefinition<T>,
        payload: T,
        actor: Actor?
    ): JobExecution {
        return enqueue(job.type, payload, actor)
    }

    override fun enqueue(
        jobType: String,
        payload: Any?,
        actor: Actor?
    ): JobExecution {
        val payloadJson = payload?.let { objectMapper.writeValueAsString(it) }
        val resolvedActor = actor ?: actorProvider.currentOrSystem()
        val execution = jobExecutionService.createQueued(
            jobType = jobType,
            payload = payloadJson,
            actor = resolvedActor
        )
        sendMessage(execution)
        return execution
    }

    fun enqueueEmail(jobType: String, payload: Any): JobExecution {
        return enqueue(jobType, payload, null)
    }

    fun requeue(execution: JobExecution) {
        sendMessage(execution)
    }

    private fun sendMessage(execution: JobExecution) {
        val executionId = execution.id
            ?: throw IllegalStateException("Cannot dispatch job ${execution.jobType} without persisted execution id")
        val message = JobMessage(executionId, execution.jobType, execution.payload)
        rabbitTemplate.convertAndSend(
            jobQueueProperties.exchangeName,
            jobQueueProperties.routingKey,
            message
        )
    }
}
