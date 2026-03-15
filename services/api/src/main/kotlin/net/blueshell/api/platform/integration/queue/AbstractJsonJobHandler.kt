package net.blueshell.api.platform.integration.queue

import tools.jackson.databind.ObjectMapper
import org.springframework.transaction.annotation.Transactional

abstract class AbstractJsonJobHandler<T : Any>(
    private val objectMapper: ObjectMapper,
    private val payloadType: Class<T>
) : JobHandler {

    /**
     * Thread-local execution ID so handlers can forward it to downstream services
     * (e.g. to link an email outbox record back to the job that triggered it).
     * Using a thread-local is safe because @Async jobs each run on their own thread.
     */
    private val executionIdLocal = ThreadLocal<Long?>()

    protected val currentExecutionId: Long?
        get() = executionIdLocal.get()

    @Transactional
    override fun handle(payload: String?, executionId: Long?) {
        val body = payload ?: throw IllegalArgumentException("Payload required for job type $jobType")
        executionIdLocal.set(executionId)
        try {
            val parsed = objectMapper.readValue(body, payloadType)
            handlePayload(parsed)
        } finally {
            executionIdLocal.remove()
        }
    }

    protected abstract fun handlePayload(payload: T)
}
