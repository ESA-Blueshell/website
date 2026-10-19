package net.blueshell.api.declaration.domain

import net.blueshell.api.email.api.EmailJob
import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.job.requireExists
import net.blueshell.api.user.api.UserService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class DeclarationReturnedEmailJob(
    objectMapper: ObjectMapper,
    private val users: UserService,
    emails: EmailSenderService,
    @param:Value($$"${frontend.url}") private val frontendUrl: String,
) : EmailJob<DeclarationJobs.DeclarationReturnedPayload>(objectMapper, DeclarationJobs.DeclarationReturned, emails) {
    // Its payload holds no link or token, and it is meant for the submitter wherever their mail now goes.
    override val resendable = true

    override fun compose(payload: DeclarationJobs.DeclarationReturnedPayload): EmailContent =
        declarationReturnedEmail(requireExists { users.findById(payload.submitterId) }, payload, frontendUrl)
}
