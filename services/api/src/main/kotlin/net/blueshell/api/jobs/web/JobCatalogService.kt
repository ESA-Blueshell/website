package net.blueshell.api.jobs.web

import net.blueshell.api.jobs.persistence.JobExecution
import net.blueshell.api.jobs.domain.JobDispatcher
import net.blueshell.api.jobs.domain.JobHandlerRegistry
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.ObjectMapper
import java.lang.reflect.Modifier

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
        return jobDispatcher.runAsync(jobType, typedPayload, actor = null, dedupKey = null)
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

    /**
     * Reflects payload fields via plain Java reflection on the declared fields.
     * Kotlin keeps the real property names on the backing fields (unlike
     * constructor parameter names, which need kotlin-reflect), and Java
     * reflection uses the class's own loader — so this works under the dev
     * DevTools restart classloader where `KClass.primaryConstructor` returns null.
     * A non-null Kotlin primitive compiles to a primitive field, which is how we
     * infer "required".
     */
    private fun reflectFields(payloadType: Class<*>): List<JobPayloadFieldDTO> =
        payloadType.declaredFields
            .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) || it.type == Unit::class.java }
            .map { field ->
                val kind = classifyKind(field.type)
                JobPayloadFieldDTO(
                    name = field.name,
                    type = normalizeType(field.type),
                    kind = kind,
                    required = field.type.isPrimitive,
                    enumValues = if (kind == JobPayloadFieldKind.ENUM) enumConstantNames(field.type) else null,
                )
            }

    private fun classifyKind(type: Class<*>): JobPayloadFieldKind = when {
        type.isEnum -> JobPayloadFieldKind.ENUM
        type.isPrimitive || isPrimitiveWrapper(type) || type == String::class.java -> JobPayloadFieldKind.PRIMITIVE
        else -> JobPayloadFieldKind.OBJECT
    }

    private fun enumConstantNames(type: Class<*>): List<String> =
        type.enumConstants?.mapNotNull { (it as? Enum<*>)?.name } ?: emptyList()

    private fun isPrimitiveWrapper(type: Class<*>): Boolean = type in PRIMITIVE_WRAPPERS

    private fun normalizeType(type: Class<*>): String = when (type) {
        java.lang.Long.TYPE -> "Long"
        Integer.TYPE -> "Int"
        java.lang.Short.TYPE -> "Short"
        java.lang.Double.TYPE -> "Double"
        java.lang.Float.TYPE -> "Float"
        java.lang.Boolean.TYPE -> "Boolean"
        else -> type.simpleName
    }

    companion object {
        private val PRIMITIVE_WRAPPERS: Set<Class<*>> = setOf(
            java.lang.Long::class.java,
            java.lang.Integer::class.java,
            java.lang.Short::class.java,
            java.lang.Double::class.java,
            java.lang.Float::class.java,
            java.lang.Boolean::class.java,
            java.lang.Byte::class.java,
            java.lang.Character::class.java,
        )
    }
}
