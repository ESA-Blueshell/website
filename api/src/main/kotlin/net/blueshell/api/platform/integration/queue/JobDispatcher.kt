package net.blueshell.api.platform.integration.queue

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.platform.config.JobQueueProperties
import net.blueshell.api.platform.integration.job.model.JobExecution
import net.blueshell.api.platform.integration.job.service.JobExecutionService
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Service

@Service
class JobDispatcher(
    private val rabbitTemplate: RabbitTemplate,
    private val objectMapper: ObjectMapper,
    private val jobQueueProperties: JobQueueProperties,
    private val jobExecutionService: JobExecutionService
) {
    fun <T : Any> enqueue(job: JobDefinition<T>, payload: T): JobExecution {
        return enqueue(job.type, payload)
    }

    fun enqueue(jobType: String, payload: Any? = null): JobExecution {
        val payloadJson = payload?.let { objectMapper.writeValueAsString(it) }
        val execution = jobExecutionService.createQueued(jobType, payloadJson)
        sendMessage(execution)
        return execution
    }

    fun enqueueEmail(jobType: String, payload: Any): JobExecution {
        return enqueue(jobType, payload)
    }

    fun requeue(execution: JobExecution) {
        sendMessage(execution)
    }

    private fun sendMessage(execution: JobExecution) {
        val executionId = execution.id ?: return
        val message = JobMessage(executionId, execution.jobType, execution.payload)
        rabbitTemplate.convertAndSend(
            jobQueueProperties.exchangeName,
            jobQueueProperties.routingKey,
            message
        )
    }
}
