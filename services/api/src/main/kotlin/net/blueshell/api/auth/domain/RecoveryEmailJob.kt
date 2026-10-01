package net.blueshell.api.auth.domain

import net.blueshell.api.email.api.EmailJob
import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.job.requireExists
import net.blueshell.api.user.api.UserService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class RecoveryEmailJob(
    objectMapper: ObjectMapper,
    private val users: UserService,
    private val emails: EmailSenderService,
    @param:Value($$"${frontend.url}") private val frontendUrl: String,
    private val contacts: SecurityContacts,
) : EmailJob<AuthJobs.RecoveryPayload>(
        objectMapper,
        AuthJobs.Recovery,
        emails,
        "email.recovery",
    ) {
    override fun compose(payload: AuthJobs.RecoveryPayload): EmailContent {
        val user = requireExists { users.findById(payload.userId) }
        return buildRecoveryEmail(payload.tokenPurpose, user, payload.token, frontendUrl, contacts)
    }
}
