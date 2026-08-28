package net.blueshell.api.domain.auth.application.job

import net.blueshell.api.domain.auth.application.email.buildRecoveryEmail
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.jobs.api.AbstractJsonJobHandler
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.requireExists
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class RecoveryEmailJob(
    objectMapper: ObjectMapper,
    private val users: UserService,
    private val emails: EmailSenderService,
    @param:Value($$"${frontend.url}") private val frontendUrl: String,
) : AbstractJsonJobHandler<EmailJobs.RecoveryPayload>(
    objectMapper,
    EmailJobs.Recovery.payloadType,
) {
    override val jobType: String = EmailJobs.Recovery.type

    override fun handlePayload(payload: EmailJobs.RecoveryPayload) {
        val user = requireExists { users.findById(payload.userId) }
        log.info("Sending {} email for user={}", payload.tokenPurpose, payload.userId)
        emails.send(
            buildRecoveryEmail(payload.tokenPurpose, user, payload.token, frontendUrl),
            "email.recovery",
            currentExecutionId,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(RecoveryEmailJob::class.java)
    }
}
