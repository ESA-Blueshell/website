package net.blueshell.api.platform.integration.job.web.service

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.event.application.EventSignUpService
import net.blueshell.api.domain.event.application.EventService
import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.event.persistence.EventSignUp
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.cohort.persistence.Cohort
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.job.web.dto.JobExecutionDTO
import net.blueshell.api.platform.integration.job.web.dto.JobExecutionRelatedEntityDTO
import net.blueshell.api.platform.integration.job.persistence.JobExecution
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
    private val eventService: EventService,
    private val eventSignUpService: EventSignUpService,
    private val contributionPeriodService: ContributionPeriodService,
    private val cohortRepository: CohortRepository,
) {

    fun toDtos(executions: List<JobExecution>): List<JobExecutionDTO> {
        val userCache = mutableMapOf<Long, User?>()
        val eventCache = mutableMapOf<Long, Event?>()
        val signUpCache = mutableMapOf<Long, EventSignUp?>()
        val periodCache = mutableMapOf<Long, ContributionPeriod?>()
        val cohortCache = mutableMapOf<Long, Cohort?>()

        return executions.map { execution ->
            toDto(
                execution = execution,
                userCache = userCache,
                eventCache = eventCache,
                signUpCache = signUpCache,
                periodCache = periodCache,
                cohortCache = cohortCache,
            )
        }
    }

    fun toDto(execution: JobExecution): JobExecutionDTO = toDtos(listOf(execution)).first()

    private fun toDto(
        execution: JobExecution,
        userCache: MutableMap<Long, User?>,
        eventCache: MutableMap<Long, Event?>,
        signUpCache: MutableMap<Long, EventSignUp?>,
        periodCache: MutableMap<Long, ContributionPeriod?>,
        cohortCache: MutableMap<Long, Cohort?>,
    ): JobExecutionDTO {
        val parsedPayload = parsePayload(execution.payload)
        val relatedEntities = buildRelatedEntities(
            payload = parsedPayload,
            userCache = userCache,
            eventCache = eventCache,
            signUpCache = signUpCache,
            periodCache = periodCache,
            cohortCache = cohortCache,
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
        eventCache: MutableMap<Long, Event?>,
        signUpCache: MutableMap<Long, EventSignUp?>,
        periodCache: MutableMap<Long, ContributionPeriod?>,
        cohortCache: MutableMap<Long, Cohort?>,
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
                findUserOrNull(userId)
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
                findEventSignUpOrNull(signUpId)
            }
            add(type = "EVENT_SIGNUP", id = signUpId, label = "Event sign-up #$signUpId")

            val signUpUserId = signUp?.userId
            if (signUpUserId != null) {
                val user = getOrPutNullable(userCache, signUpUserId) {
                    findUserOrNull(signUpUserId)
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
                findContributionPeriodOrNull(periodId)
            }
            add(type = "CONTRIBUTION_PERIOD", id = periodId, label = periodLabel(periodId, period))
        }

        payload.cohortId?.let { cohortId ->
            val cohort = getOrPutNullable(cohortCache, cohortId) {
                cohortRepository.findById(cohortId).orElse(null)
            }
            add(type = "COHORT", id = cohortId, label = cohortLabel(cohortId, cohort))
        }

        return entities.values.toList()
    }

    private fun cohortLabel(cohortId: Long, cohort: Cohort?): String {
        if (cohort == null) return "Cohort #$cohortId"
        return "${cohort.label} (${cohort.system} ${cohort.kind})"
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
            userId = root.longValue("userId"),
            eventId = root.longValue("eventId"),
            eventSignUpId = root.longValue("eventSignUpId"),
            contributionPeriodId = root.longValue("contributionPeriodId") ?: root.longValue("periodId"),
            cohortId = root.longValue("cohortId"),
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

    private fun findEventSignUpOrNull(signUpId: Long): EventSignUp? {
        return findOrNull { eventSignUpService.findById(signUpId) }
    }

    private fun findContributionPeriodOrNull(periodId: Long): ContributionPeriod? {
        return findOrNull { contributionPeriodService.findById(periodId) }
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
        val userId: Long? = null,
        val eventId: Long? = null,
        val eventSignUpId: Long? = null,
        val contributionPeriodId: Long? = null,
        val cohortId: Long? = null,
        val system: ContactSystem? = null,
        /** The full payload as a plain map; the admin UI renders unknown fields itself. */
        val raw: Map<String, Any?>? = null,
    )
}
