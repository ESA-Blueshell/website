package net.blueshell.api.queue

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.config.JobQueueProperties
import net.blueshell.api.model.job.JobExecution
import net.blueshell.api.service.job.JobExecutionService
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Service

@Service
class JobDispatcher(
    private val rabbitTemplate: RabbitTemplate,
    private val objectMapper: ObjectMapper,
    private val jobQueueProperties: JobQueueProperties,
    private val jobExecutionService: JobExecutionService
) {
    fun enqueue(jobType: String, payload: Any? = null): JobExecution {
        val payloadJson = payload?.let { objectMapper.writeValueAsString(it) }
        val execution = jobExecutionService.createQueued(jobType, payloadJson)
        sendMessage(execution)
        return execution
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
