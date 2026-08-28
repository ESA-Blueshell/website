package net.blueshell.api.domain.event.application

import net.blueshell.api.jobs.web.JobSubject
import net.blueshell.api.jobs.web.JobSubjectResolver
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

@Component
@Order(30)
class EventSignUpJobSubjectResolver(
    private val signUps: EventSignUpService,
) : JobSubjectResolver {
    override val payloadFields = listOf("eventSignUpId")
    override val entityType = "EVENT_SIGNUP"

    override fun label(id: Long): String = "Event sign-up #$id"

    /** A sign-up names its member and its event, both worth showing beside the job. */
    override fun implied(id: Long): List<JobSubject> {
        val signUp = findOrNull(id) ?: return emptyList()
        return buildList {
            signUp.userId?.let { add(JobSubject("userId", it)) }
            signUp.eventId.takeIf { it > 0 }?.let { add(JobSubject("eventId", it)) }
        }
    }

    private fun findOrNull(id: Long) = try {
        signUps.findById(id)
    } catch (error: ResponseStatusException) {
        if (error.statusCode == HttpStatus.NOT_FOUND) null else throw error
    }
}
