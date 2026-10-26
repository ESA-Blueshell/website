package net.blueshell.api.auth.domain

import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.jobs.api.AbstractJsonJobHandler
import net.blueshell.api.shared.job.requireExists
import net.blueshell.api.user.api.UserService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

/**
 * Sends the email that carries one purpose's link. Open because [AbstractJsonJobHandler.handle] is
 * `@Transactional` and Spring subclasses this to proxy it; built per email by [RecoveryEmailHandlers].
 */
open class RecoveryEmailHandler(
    objectMapper: ObjectMapper,
    private val email: AuthJobs.RecoveryEmail,
    private val users: UserService,
    private val emails: EmailSenderService,
    private val frontendUrl: String,
    private val contacts: SecurityContacts,
) : AbstractJsonJobHandler<AuthJobs.RecoveryPayload>(objectMapper, email) {
    override fun handlePayload(payload: AuthJobs.RecoveryPayload) {
        val user = requireExists { users.findById(payload.userId) }
        log.info("Sending {} email for user={}", email.purpose, payload.userId)
        emails.send(
            buildRecoveryEmail(email.purpose, user, payload.token, frontendUrl, contacts),
            "email.recovery",
            currentExecutionId,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(RecoveryEmailHandler::class.java)
    }
}

/** One [RecoveryEmailHandler] per email, each a bean because the queue finds handlers by injecting every one. */
@Configuration
class RecoveryEmailHandlers(
    private val objectMapper: ObjectMapper,
    private val users: UserService,
    private val emails: EmailSenderService,
    @param:Value($$"${frontend.url}") private val frontendUrl: String,
    private val contacts: SecurityContacts,
) {
    private fun jobFor(email: AuthJobs.RecoveryEmail) = RecoveryEmailHandler(objectMapper, email, users, emails, frontendUrl, contacts)

    @Bean
    fun accountActivationEmailJob() = jobFor(AuthJobs.AccountActivation)

    @Bean
    fun memberActivationEmailJob() = jobFor(AuthJobs.MemberActivation)

    @Bean
    fun passwordResetEmailJob() = jobFor(AuthJobs.PasswordReset)

    @Bean
    fun emailChangeEmailJob() = jobFor(AuthJobs.EmailChange)

    @Bean
    fun twoFactorReenrolmentEmailJob() = jobFor(AuthJobs.TwoFactorReenrolment)
}
