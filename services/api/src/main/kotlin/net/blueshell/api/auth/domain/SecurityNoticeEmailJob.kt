package net.blueshell.api.auth.domain

import net.blueshell.api.auth.persistence.SecurityEventRepository
import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.jobs.api.AbstractJsonJobHandler
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.EmailJobs.SecurityNoticeAudience
import net.blueshell.api.shared.job.requireExists
import net.blueshell.api.user.api.UserService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class SecurityNoticeEmailJob(
    objectMapper: ObjectMapper,
    private val events: SecurityEventRepository,
    private val users: UserService,
    private val emails: EmailSenderService,
    @param:Value($$"${frontend.url}") private val frontendUrl: String,
    @param:Value($$"${app.url}") private val apiUrl: String,
    @param:Value($$"${app.security.contact-email:board@blueshell.utwente.nl}") private val contactEmail: String,
) : AbstractJsonJobHandler<EmailJobs.SecurityNoticePayload>(
        objectMapper,
        EmailJobs.SecurityNotice.payloadType,
    ) {
    override val jobType: String = EmailJobs.SecurityNotice.type

    override fun handlePayload(payload: EmailJobs.SecurityNoticePayload) {
        val event = requireExists { requireNotNull(events.findWithPeopleById(payload.securityEventId)) }
        val (email, name) =
            when (payload.audience) {
                SecurityNoticeAudience.PERSON -> event.subject.email to event.subject.fullName
                SecurityNoticeAudience.OLD_ADDRESS -> requireNotNull(payload.recipientEmail) to event.subject.fullName
                SecurityNoticeAudience.ADMINISTRATOR ->
                    requireExists { users.findById(requireNotNull(payload.recipientUserId)) }.let { it.email to it.fullName }
            }
        val contacts = SecurityContacts(contactEmail, "$apiUrl/discord/channel/board", "$apiUrl/discord/channel/suggestions")
        emails.send(
            createSecurityNoticeEmail(event, payload.audience, email, name, payload.lockToken, frontendUrl, contacts),
            EmailJobs.SecurityNotice.type,
            currentExecutionId,
        )
    }
}
