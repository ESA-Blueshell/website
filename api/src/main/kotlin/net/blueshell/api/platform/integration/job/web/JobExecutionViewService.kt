package net.blueshell.api.platform.integration.job.web

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.contribution.persistence.repository.ContributionPeriodRepository
import net.blueshell.api.domain.event.application.EventService
import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.event.persistence.EventSignUp
import net.blueshell.api.domain.event.persistence.repository.EventSignUpRepository
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.domain.user.persistence.repository.UserRepository
import net.blueshell.api.platform.integration.job.dto.JobExecutionDTO
import net.blueshell.api.platform.integration.job.dto.JobExecutionRelatedEntityDTO
import net.blueshell.api.platform.integration.job.model.JobExecution
import net.blueshell.api.shared.enums.ActionActorType
import net.blueshell.api.shared.enums.JobExecutionCategory
import org.springframework.stereotype.Service

@Service
class JobExecutionViewService(
    private val objectMapper: ObjectMapper,
    private val userRepository: UserRepository,
    private val eventService: EventService,
    private val eventSignUpRepository: EventSignUpRepository,
    private val contributionPeriodRepository: ContributionPeriodRepository
) {

    fun toDtos(executions: List<JobExecution>): List<JobExecutionDTO> {
        val userCache = mutableMapOf<Long, User?>()
        val eventCache = mutableMapOf<Long, Event?>()
        val signUpCache = mutableMapOf<Long, EventSignUp?>()
        val periodCache = mutableMapOf<Long, ContributionPeriod?>()

        return executions.map { execution ->
            toDto(
                execution = execution,
                userCache = userCache,
                eventCache = eventCache,
                signUpCache = signUpCache,
                periodCache = periodCache
            )
        }
    }

    fun toDto(execution: JobExecution): JobExecutionDTO {
        return toDtos(listOf(execution)).first()
    }

    private fun toDto(
        execution: JobExecution,
        userCache: MutableMap<Long, User?>,
        eventCache: MutableMap<Long, Event?>,
        signUpCache: MutableMap<Long, EventSignUp?>,
        periodCache: MutableMap<Long, ContributionPeriod?>
    ): JobExecutionDTO {
        val parsedPayload = parsePayload(execution.payload)
        val relatedEntities = buildRelatedEntities(
            payload = parsedPayload,
            userCache = userCache,
            eventCache = eventCache,
            signUpCache = signUpCache,
            periodCache = periodCache
        )
        val initiatedByUser = execution.initiatedByUserId?.let { userId ->
            getOrPutNullable(userCache, userId) {
                userRepository.findById(userId).orElse(null)
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
            summary = buildSummary(execution.jobType, relatedEntities),
            status = execution.status,
            errorMessage = execution.errorMessage,
            errorType = execution.errorType,
            errorReason = displayReason,
            stackTrace = stackTrace,
            attempts = execution.attempts,
            queuedAt = execution.queuedAt,
            startedAt = execution.startedAt,
            finishedAt = execution.finishedAt,
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
        eventCache: MutableMap<Long, Event?>,
        signUpCache: MutableMap<Long, EventSignUp?>,
        periodCache: MutableMap<Long, ContributionPeriod?>
    ): List<JobExecutionRelatedEntityDTO> {
        val entities = linkedMapOf<String, JobExecutionRelatedEntityDTO>()

        fun add(type: String, id: Long?, label: String) {
            val key = "$type:${id ?: "none"}"
            if (!entities.containsKey(key)) {
                entities[key] = JobExecutionRelatedEntityDTO(type = type, id = id, label = label)
            }
        }

        payload.userId?.let { userId ->
            val user = getOrPutNullable(userCache, userId) {
                userRepository.findById(userId).orElse(null)
            }
            add(type = "USER", id = userId, label = userLabel(userId, user))
        }

        payload.eventId?.let { eventId ->
            val event = getOrPutNullable(eventCache, eventId) {
                eventService.findByIdIncludingDeletedOrNull(eventId)
            }
            add(type = "EVENT", id = eventId, label = eventLabel(eventId, event))
        }

        payload.eventSignUpId?.let { signUpId ->
            val signUp = getOrPutNullable(signUpCache, signUpId) {
                eventSignUpRepository.findById(signUpId).orElse(null)
            }
            add(type = "EVENT_SIGNUP", id = signUpId, label = "Event sign-up #$signUpId")

            val signUpUserId = signUp?.userId
            if (signUpUserId != null) {
                val user = getOrPutNullable(userCache, signUpUserId) {
                    userRepository.findById(signUpUserId).orElse(null)
                }
                add(type = "USER", id = signUpUserId, label = userLabel(signUpUserId, user))
            }

            val signUpEventId = signUp?.eventId?.takeIf { it > 0 }
            if (signUpEventId != null) {
                val event = getOrPutNullable(eventCache, signUpEventId) {
                    eventService.findByIdIncludingDeletedOrNull(signUpEventId)
                }
                add(type = "EVENT", id = signUpEventId, label = eventLabel(signUpEventId, event))
            }
        }

        payload.contributionPeriodId?.let { periodId ->
            val period = getOrPutNullable(periodCache, periodId) {
                contributionPeriodRepository.findById(periodId).orElse(null)
            }
            add(type = "CONTRIBUTION_PERIOD", id = periodId, label = periodLabel(periodId, period))
        }

        return entities.values.toList()
    }

    private fun periodLabel(periodId: Long, period: ContributionPeriod?): String {
        if (period == null) {
            return "Contribution period #$periodId"
        }
        return "Contribution period #$periodId (${period.startDate} - ${period.endDate})"
    }

    private fun eventLabel(eventId: Long, event: Event?): String {
        if (event == null) {
            return "Event #$eventId"
        }
        return "Event #$eventId: ${event.title}"
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

            else -> JobExecutionCategory.other
        }
    }

    private fun buildSummary(jobType: String, relatedEntities: List<JobExecutionRelatedEntityDTO>): String {
        val primary = relatedEntities.firstOrNull()?.label
        return when {
            jobType.startsWith("calendar.") -> primary?.let { "Calendar sync for $it" } ?: "Calendar synchronization"
            jobType.startsWith("contact.") -> primary?.let { "Contact sync for $it" } ?: "Contact synchronization"
            jobType == "email.recovery" -> primary?.let { "Recovery email for $it" } ?: "Recovery email"
            jobType == "email.event-signup" -> primary?.let { "Event sign-up email for $it" } ?: "Event sign-up email"
            jobType == "email.contribution-reminder" -> {
                primary?.let { "Contribution reminder for $it" } ?: "Contribution reminder email"
            }

            else -> humanizeJobType(jobType)
        }
    }

    private fun humanizeJobType(jobType: String): String {
        return jobType
            .replace('.', ' ')
            .replace('-', ' ')
            .replace('_', ' ')
            .trim()
            .split(Regex("\\s+"))
            .joinToString(" ") { token ->
                token.lowercase().replaceFirstChar { ch -> ch.uppercase() }
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
        return ParsedPayload(
            userId = root.longValue("userId"),
            eventId = root.longValue("eventId"),
            eventSignUpId = root.longValue("eventSignUpId"),
            contributionPeriodId = root.longValue("contributionPeriodId") ?: root.longValue("periodId")
        )
    }

    private fun JsonNode.longValue(field: String): Long? {
        val node = get(field) ?: return null
        if (node.isNull) return null
        return if (node.canConvertToLong()) node.asLong() else null
    }

    private fun <K, V> getOrPutNullable(cache: MutableMap<K, V?>, key: K, supplier: () -> V?): V? {
        if (!cache.containsKey(key)) {
            cache[key] = supplier()
        }
        return cache[key]
    }

    private data class ParsedPayload(
        val userId: Long? = null,
        val eventId: Long? = null,
        val eventSignUpId: Long? = null,
        val contributionPeriodId: Long? = null
    )
}
