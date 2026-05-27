package net.blueshell.api.platform.integration.job.web.service

import net.blueshell.api.platform.integration.job.persistence.JobExecution
import net.blueshell.api.platform.integration.job.web.dto.JobPayloadFieldDTO
import net.blueshell.api.platform.integration.job.web.dto.JobTypeDescriptorDTO
import net.blueshell.api.platform.integration.queue.JobDispatcher
import net.blueshell.api.platform.integration.queue.JobHandlerRegistry
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.ObjectMapper
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

/**
 * Backs the manual job-trigger admin page: lists the triggerable job types with
 * their reflected payload shape, and enqueues a job from a raw JSON payload.
 */
@Component
class JobCatalogService(
    private val registry: JobHandlerRegistry,
    private val jobDispatcher: JobDispatcher,
    private val objectMapper: ObjectMapper,
) {
    fun describe(): List<JobTypeDescriptorDTO> =
        registry.jobTypes().sorted().map { type ->
            JobTypeDescriptorDTO(
                type = type,
                payloadFields = registry.payloadType(type)?.let(::reflectFields) ?: emptyList(),
            )
        }

    fun enqueue(jobType: String, payload: Map<String, Any?>?): JobExecution {
        val payloadType = registry.payloadType(jobType)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown job type '$jobType'")
        val typedPayload = deserializePayload(payload, payloadType, jobType)
        // dedupKey = null on purpose: a manual trigger should always run even if an
        // identical job is queued/running. The retry-supersede flow collapses dups.
        return jobDispatcher.enqueue(jobType, typedPayload, actor = null, dedupKey = null)
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Job '$jobType' was not enqueued")
    }

    private fun deserializePayload(payload: Map<String, Any?>?, payloadType: Class<*>, jobType: String): Any =
        try {
            objectMapper.convertValue(payload ?: emptyMap<String, Any?>(), payloadType)
        } catch (e: Exception) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid payload for job type '$jobType': ${e.message}",
            )
        }

    private fun reflectFields(payloadType: Class<*>): List<JobPayloadFieldDTO> {
        val ctor = payloadType.kotlin.primaryConstructor ?: return emptyList()
        return ctor.parameters.mapNotNull { param ->
            val name = param.name ?: return@mapNotNull null
            JobPayloadFieldDTO(
                name = name,
                type = (param.type.classifier as? KClass<*>)?.simpleName ?: param.type.toString(),
                required = !param.type.isMarkedNullable && !param.isOptional,
            )
        }
    }
}
