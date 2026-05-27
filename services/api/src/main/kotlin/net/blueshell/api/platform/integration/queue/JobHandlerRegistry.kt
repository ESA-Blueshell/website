package net.blueshell.api.platform.integration.queue

import org.springframework.stereotype.Component

@Component
class JobHandlerRegistry(handlers: List<JobHandler>) {
    private val handlerMap: Map<String, JobHandler> = handlers.associateBy { it.jobType }

    fun get(jobType: String): JobHandler? = handlerMap[jobType]

    fun jobTypes(): Set<String> = handlerMap.keys

    fun payloadType(jobType: String): Class<*>? = handlerMap[jobType]?.payloadType
}
