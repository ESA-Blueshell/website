package net.blueshell.api.auth.domain

import net.blueshell.api.auth.domain.AuthJobs.SecurityNotificationAudience
import net.blueshell.api.auth.persistence.SecurityEventRepository
import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.jobs.api.AbstractJsonJobHandler
import net.blueshell.api.shared.job.requireExists
import net.blueshell.api.user.api.UserService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class SecurityNotificationEmailJob(
    objectMapper: ObjectMapper,
    private val events: SecurityEventRepository,
    private val users: UserService,
    private val emails: EmailSenderService,
    @param:Value($$"${frontend.url}") private val frontendUrl: String,
    private val contacts: SecurityContacts,
) : AbstractJsonJobHandler<AuthJobs.SecurityNotificationPayload>(
        objectMapper,
        AuthJobs.SecurityNotification,
    ) {
    override fun handlePayload(payload: AuthJobs.SecurityNotificationPayload) {
        val event = requireExists { requireNotNull(events.findWithPeopleById(payload.securityEventId)) }
        val (email, name) =
            when (payload.audience) {
                SecurityNotificationAudience.PERSON -> event.subject.email to event.subject.fullName
                SecurityNotificationAudience.OLD_ADDRESS -> requireNotNull(payload.recipientEmail) to event.subject.fullName
                SecurityNotificationAudience.ADMINISTRATOR ->
                    requireExists { users.findById(requireNotNull(payload.recipientUserId)) }.let { it.email to it.fullName }
            }
        emails.send(
            createSecurityNotificationEmail(event, payload.audience, email, name, payload.lockToken, frontendUrl, contacts),
            AuthJobs.SecurityNotification.type,
            currentExecutionId,
        )
    }
}
