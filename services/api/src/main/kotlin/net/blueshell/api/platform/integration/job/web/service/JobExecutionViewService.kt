package net.blueshell.api.platform.integration.job.web.service

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.job.web.dto.JobExecutionDTO
import net.blueshell.api.platform.integration.job.web.dto.JobExecutionRelatedEntityDTO
import net.blueshell.api.platform.integration.job.persistence.JobExecution
import net.blueshell.api.platform.integration.job.web.port.JobSubject
import net.blueshell.api.platform.integration.job.web.port.JobSubjectResolver
import net.blueshell.api.shared.enums.ActionActorType
import net.blueshell.api.shared.enums.ContactSystem
import net.blueshell.api.shared.enums.JobExecutionCategory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

@Component
class JobExecutionViewService(
    private val objectMapper: ObjectMapper,
    private val userService: UserService,
    /** Ordered by `@Order`, which fixes the order related entities are listed in. */
    private val subjectResolvers: List<JobSubjectResolver>,
) {
    private val resolversByField: Map<String, JobSubjectResolver> =
        subjectResolvers.flatMap { r -> r.payloadFields.map { it to r } }.toMap()

    private companion object {
        /** The job module resolves its own initiator and subject users. */
        const val USER_FIELD = "userId"
        const val USER_TYPE = "USER"
    }


    fun toDtos(executions: List<JobExecution>): List<JobExecutionDTO> {
        val userCache = mutableMapOf<Long, User?>()
        val labelCache = mutableMapOf<String, String>()

        return executions.map { execution ->
            toDto(execution = execution, userCache = userCache, labelCache = labelCache)
        }
    }

    fun toDto(execution: JobExecution): JobExecutionDTO = toDtos(listOf(execution)).first()

    private fun toDto(
        execution: JobExecution,
        userCache: MutableMap<Long, User?>,
        labelCache: MutableMap<String, String>,
    ): JobExecutionDTO {
        val parsedPayload = parsePayload(execution.payload)
        val relatedEntities = buildRelatedEntities(
            payload = parsedPayload,
            userCache = userCache,
            labelCache = labelCache,
        )
        val initiatedByUser = execution.initiatedByUserId?.let { userId ->
            getOrPutNullable(userCache, userId) {
                findUserOrNull(userId)
            }
        }
        val stackTrace = extractStackTrace(execution.errorReason)
        val displayReason = if (stackTrace != null) {
            execution.errorMessage ?: stackTrace.lineSequence().firstOrNull()
        } else {
            execution.errorReason ?: execution.errorMessage
        }

        return JobExecutionDTO(
            id = execution.id,
            jobType = execution.jobType,
            category = categoryFor(execution.jobType),
            targetSystem = parsedPayload.system,
            status = execution.status,
            errorMessage = execution.errorMessage,
            errorType = execution.errorType,
            errorReason = displayReason,
            stackTrace = stackTrace,
            attempts = execution.attempts,
            dedupKey = execution.dedupKey,
            payload = parsedPayload.raw,
            queuedAt = execution.queuedAt,
            startedAt = execution.startedAt,
            finishedAt = execution.finishedAt,
            nextAttemptAt = execution.nextAttemptAt,
            actor = execution.actor,
            initiatedByUserId = execution.initiatedByUserId,
            initiatedByType = execution.initiatedByType,
            initiatedByRole = execution.initiatedByRole,
            initiatedByDisplay = formatInitiator(execution, initiatedByUser),
            initiatedByUsername = initiatedByUser?.username,
            initiatedByFullName = initiatedByUser?.fullName,
            relatedEntities = relatedEntities,
            createdAt = execution.createdAt,
            updatedAt = execution.updatedAt
        )
    }

    private fun buildRelatedEntities(
        payload: ParsedPayload,
        userCache: MutableMap<Long, User?>,
        labelCache: MutableMap<String, String>,
    ): List<JobExecutionRelatedEntityDTO> {
        val entities = linkedMapOf<String, JobExecutionRelatedEntityDTO>()

        fun add(type: String, id: Long?, label: String) {
            val key = "$type:${id ?: "none"}"
            if (!entities.containsKey(key)) {
                entities[key] = JobExecutionRelatedEntityDTO(type = type, id = id, label = label)
            }
        }

        fun addSubject(subject: JobSubject) {
            if (subject.field == USER_FIELD) {
                val user = getOrPutNullable(userCache, subject.id) { findUserOrNull(subject.id) }
                add(type = USER_TYPE, id = subject.id, label = userLabel(subject.id, user))
                return
            }
            val resolver = resolversByField[subject.field] ?: return
            val key = "${resolver.entityType}:${subject.id}"
            if (entities.containsKey(key)) return
            val label = labelCache.getOrPut("${subject.field}:${subject.id}") { resolver.label(subject.id) }
            add(type = resolver.entityType, id = subject.id, label = label)
            resolver.implied(subject.id).forEach { addSubject(it) }
        }

        payload.id(USER_FIELD)?.let { addSubject(JobSubject(USER_FIELD, it)) }

        // Ordered by @Order, so the list a reader sees is stable across deploys.
        subjectResolvers.forEach { resolver ->
            resolver.payloadFields.firstNotNullOfOrNull { payload.id(it) }
                ?.let { addSubject(JobSubject(resolver.payloadFields.first(), it)) }
        }

        return entities.values.toList()
    }

    private fun userLabel(userId: Long, user: User?): String {
        if (user == null) {
            return "User #$userId"
        }
        return "${user.fullName} (@${user.username})"
    }

    private fun formatInitiator(execution: JobExecution, user: User?): String {
        if (execution.initiatedByType == ActionActorType.SYSTEM) {
            return "System"
        }
        if (user != null) {
            return "${user.fullName} (@${user.username})"
        }
        if (execution.initiatedByUserId != null) {
            return "User #${execution.initiatedByUserId}"
        }
        return execution.initiatedByType.name
    }

    private fun categoryFor(jobType: String): JobExecutionCategory {
        val normalized = jobType.trim()
        if (normalized.isBlank()) return JobExecutionCategory.other

        val lowered = normalized.lowercase()
        return when {
            lowered == "calendar" ||
                lowered.startsWith("calendar.") ||
                lowered.startsWith("calendar_") ||
                lowered.startsWith("calendar-") -> JobExecutionCategory.calendar

            lowered == "contact" ||
                lowered.startsWith("contact.") ||
                lowered.startsWith("contact_") ||
                lowered.startsWith("contact-") -> JobExecutionCategory.contact

            lowered == "email" ||
                lowered.startsWith("email.") ||
                lowered.startsWith("email_") ||
                lowered.startsWith("email-") -> JobExecutionCategory.email

            lowered == "cohort" ||
                lowered.startsWith("cohort.") ||
                lowered.startsWith("cohort_") ||
                lowered.startsWith("cohort-") -> JobExecutionCategory.cohort

            else -> JobExecutionCategory.other
        }
    }

    private fun extractStackTrace(rawReason: String?): String? {
        val value = rawReason?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return if (
            value.contains("\n\tat ") ||
            value.contains("\n at ") ||
            value.contains("Caused by:")
        ) {
            value
        } else {
            null
        }
    }

    private fun parsePayload(payload: String?): ParsedPayload {
        val raw = payload?.trim()?.takeIf { it.isNotBlank() } ?: return ParsedPayload()
        val root = runCatching { objectMapper.readTree(raw) }.getOrNull() ?: return ParsedPayload()
        val rawMap: Map<String, Any?>? = runCatching {
            @Suppress("UNCHECKED_CAST")
            objectMapper.convertValue(root, Map::class.java) as Map<String, Any?>?
        }.getOrNull()
        return ParsedPayload(
            ids = root.propertyNames().mapNotNull { name ->
                root.longValue(name)?.let { name to it }
            }.toMap(),
            system = root.contactSystem("system"),
            raw = rawMap,
        )
    }

    private fun JsonNode.contactSystem(field: String): ContactSystem? {
        val node = get(field) ?: return null
        if (node.isNull) return null
        val text = node.stringValue() ?: return null
        return runCatching { ContactSystem.valueOf(text.uppercase()) }.getOrNull()
    }

    private fun JsonNode.longValue(field: String): Long? {
        val node = get(field) ?: return null
        if (node.isNull) return null
        return if (node.canConvertToLong()) node.asLong() else null
    }

    private fun findUserOrNull(userId: Long): User? {
        return findOrNull { userService.findById(userId) }
    }

    private fun <T> findOrNull(fetcher: () -> T): T? {
        return try {
            fetcher()
        } catch (error: ResponseStatusException) {
            if (error.statusCode == HttpStatus.NOT_FOUND) {
                null
            } else {
                throw error
            }
        }
    }

    private fun <K, V> getOrPutNullable(cache: MutableMap<K, V?>, key: K, supplier: () -> V?): V? {
        if (!cache.containsKey(key)) {
            cache[key] = supplier()
        }
        return cache[key]
    }

    private data class ParsedPayload(
        /** Every numeric payload field, so a resolver can name whichever it reads. */
        val ids: Map<String, Long> = emptyMap(),
        val system: ContactSystem? = null,
        /** The full payload as a plain map; the admin UI renders unknown fields itself. */
        val raw: Map<String, Any?>? = null,
    ) {
        fun id(field: String): Long? = ids[field]
    }
}
