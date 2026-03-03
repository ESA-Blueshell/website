package net.blueshell.api.platform.integration.queue

import tools.jackson.databind.ObjectMapper
import org.springframework.transaction.annotation.Transactional

abstract class AbstractJsonJobHandler<T : Any>(
    private val objectMapper: ObjectMapper,
    private val payloadType: Class<T>
) : JobHandler {
    @Transactional
    override fun handle(payload: String?) {
        val body = payload ?: throw IllegalArgumentException("Payload required for job type $jobType")
        val parsed = objectMapper.readValue(body, payloadType)
        handlePayload(parsed)
    }

    protected abstract fun handlePayload(payload: T)
}
