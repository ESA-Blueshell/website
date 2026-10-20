package net.blueshell.api.declaration.domain

import net.blueshell.api.email.api.EmailJob
import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.job.requireExists
import net.blueshell.api.user.api.UserService
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class DeclarationPaidEmailJob(
    objectMapper: ObjectMapper,
    private val users: UserService,
    emails: EmailSenderService,
) : EmailJob<DeclarationJobs.DeclarationPaidPayload>(objectMapper, DeclarationJobs.DeclarationPaid, emails) {
    // Its payload holds no link or token, and it is meant for the submitter wherever their mail now goes.
    override val resendable = true

    override fun compose(payload: DeclarationJobs.DeclarationPaidPayload): EmailContent =
        declarationPaidEmail(requireExists { users.findById(payload.submitterId) }, payload)
}
