package net.blueshell.api.mail.domain

import net.blueshell.api.email.api.EmailJob
import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.email.api.SiteMarkdownEmails
import net.blueshell.api.mail.persistence.WrittenEmailRepository
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.job.requireExists
import net.blueshell.api.user.api.UserService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.ObjectMapper

/** Sends one copy of a written email to one person, at their address as it is now. */
@Component
class WrittenEmailJob(
    objectMapper: ObjectMapper,
    emails: EmailSenderService,
    private val written: WrittenEmailRepository,
    private val users: UserService,
    private val siteMarkdown: SiteMarkdownEmails,
) : EmailJob<MailJobs.WrittenPayload>(objectMapper, MailJobs.Written, emails) {
    override fun compose(payload: MailJobs.WrittenPayload): EmailContent? {
        val email =
            requireExists {
                written
                    .findById(
                        payload.writtenEmailId,
                    ).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No such written email") }
            }
        val person = requireExists { users.findById(payload.userId) }
        if (person.email.isBlank()) return null
        return EmailContent(
            recipientEmail = person.email,
            recipientName = person.fullName,
            subject = email.subject,
            markdownContent = siteMarkdown.forEmail(email.message),
            replyToOverride = email.replyTo,
            sendingAddressId = email.sendingAddressId,
        )
    }
}
