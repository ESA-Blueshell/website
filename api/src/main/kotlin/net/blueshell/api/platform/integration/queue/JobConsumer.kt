package net.blueshell.api.platform.integration.queue

import net.blueshell.api.platform.integration.job.model.JobExecution
import net.blueshell.api.platform.integration.job.service.JobExecutionService
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class JobConsumer(
    private val jobExecutionService: JobExecutionService,
    private val jobHandlerRegistry: JobHandlerRegistry
) {
    private val logger = LoggerFactory.getLogger(JobConsumer::class.java)

    @RabbitListener(queues = ["\${app.jobs.queue-name}"])
    fun handle(message: JobMessage) {
        val execution = jobExecutionService.findByIdOrNull(message.executionId)
        if (execution == null) {
            logger.warn("Job execution {} not found; skipping message.", message.executionId)
            return
        }

        val handler = jobHandlerRegistry.get(message.jobType)
        if (handler == null) {
            jobExecutionService.markFailed(execution, "No handler registered for job type ${message.jobType}.")
            return
        }

        runJob(execution, handler, message.payload)
    }

    private fun runJob(execution: JobExecution, handler: JobHandler, payload: String?) {
        jobExecutionService.markRunning(execution)
        try {
            handler.handle(payload)
            jobExecutionService.markSuccess(execution)
        } catch (ex: Exception) {
            logger.error("Job execution {} failed.", execution.id, ex)
            jobExecutionService.markFailed(execution, ex.message ?: "Unknown error")
        }
    }
}
