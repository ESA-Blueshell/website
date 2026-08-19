package net.blueshell.api.platform.integration.email.application.service

import net.blueshell.api.domain.auth.application.email.createMemberActivationEmail
import net.blueshell.api.domain.auth.application.email.createPasswordResetEmail
import net.blueshell.api.domain.auth.application.email.createUserActivationEmail
import net.blueshell.api.domain.contribution.application.ContributionReminderService
import net.blueshell.api.domain.contribution.application.email.createContributionReminderEmail
import net.blueshell.api.domain.contribution.persistence.ContributionReminder
import net.blueshell.api.domain.event.application.EventSignUpService
import net.blueshell.api.domain.event.application.email.createEventSignupEmail
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.platform.integration.email.adapter.EmailTransportClient
import net.blueshell.api.platform.integration.email.application.service.EmailService
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.shared.job.NonRetryableJobException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class EmailSenderService(
    private val templateService: EmailTemplateService,
    private val emailClient: EmailTransportClient,
    private val users: UserService,
    private val reminders: ContributionReminderService,
    private val eventSignUps: EventSignUpService,
    private val emailService: EmailService,
    @param:Value($$"${frontend.url}") private val frontendUrl: String,
    @param:Value($$"${app.url}") private val appUrl: String,
    @param:Value($$"${email.from.name}") private val senderName: String,
    @param:Value($$"${email.from.address}") private val senderAddress: String,
    @param:Value($$"${email.reply-to}") private val defaultReplyTo: String,
) {
    fun sendContributionReminderEmail(userId: Long, contributionPeriodId: Long, jobExecutionId: Long? = null) {
        val reminder = requireExists { reminders.findById(ContributionReminder.Id(userId, contributionPeriodId)) }
        val emailContent = createContributionReminderEmail(
            reminder.user,
            reminder.contributionPeriod,
            frontendUrl
        )
        deliver(emailContent, "email.contribution-reminder", jobExecutionId)
    }

    fun sendEventSignupEmail(eventSignUpId: Long, guestAccessToken: String, jobExecutionId: Long? = null) {
        val eventSignUp = requireExists { eventSignUps.findById(eventSignUpId) }
        val emailContent = createEventSignupEmail(eventSignUp, frontendUrl, guestAccessToken)
        deliver(emailContent, "email.event-signup", jobExecutionId)
    }

    fun sendUserResetEmail(userId: Long, token: String, tokenPurpose: TokenPurpose, jobExecutionId: Long? = null) {
        val user = requireExists { users.findById(userId) }
        log.info("Sending {} email for user={}", tokenPurpose, userId)

        val emailContent = when (tokenPurpose) {
            TokenPurpose.MEMBER_ACTIVATION -> createMemberActivationEmail(user, token, frontendUrl)
            TokenPurpose.USER_ACTIVATION -> createUserActivationEmail(user, token, frontendUrl)
            TokenPurpose.PASSWORD_RESET -> createPasswordResetEmail(user, token, frontendUrl)
            // Never emailed by design (ADR-024) — fail loudly rather than leak it.
            TokenPurpose.SIGNUP_CONTINUATION -> throw IllegalArgumentException(
                "A ${TokenPurpose.SIGNUP_CONTINUATION} token must never be emailed",
            )
        }

        deliver(emailContent, "email.recovery", jobExecutionId)
    }

    /** Render template, inject tracking pixel, create the outbox record, then hand off to the transport. */
    private fun deliver(emailContent: EmailContent, emailType: String, jobExecutionId: Long? = null) {
        val htmlContent = templateService.createEmail(
            emailContent.recipientEmail,
            emailContent.recipientName,
            emailContent.subject,
            emailContent.markdownContent
        )

        val outbox = emailService.createPending(emailContent, emailType, jobExecutionId)
        val trackedHtml = outbox.trackingToken
            ?.let { token -> injectTrackingPixel(htmlContent, "$appUrl/track/email/open/$token") }
            ?: htmlContent

        try {
            val messageId = emailClient.send(
                emailContent.recipientEmail,
                emailContent.recipientName,
                emailContent.subject,
                trackedHtml,
                emailContent.senderNameOverride ?: senderName,
                senderAddress,
                emailContent.replyToOverride ?: defaultReplyTo,
            )
            log.info("Sent email to {} subject='{}'", emailContent.recipientEmail, emailContent.subject)
            emailService.markSent(outbox, messageId)
        } catch (e: Exception) {
            log.error("Failed to send email to {} subject='{}': {}", emailContent.recipientEmail, emailContent.subject, e.message, e)
            emailService.markFailed(outbox, e.javaClass.simpleName, e.message ?: "Send error")
            throw RuntimeException("Failed to send email", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(EmailSenderService::class.java)

        /**
         * Injects a 1x1 tracking pixel before </body>.
         * When email clients load remote images the pixel fires GET /track/email/open/{token},
         * which marks the outbox entry as OPENED (and infers DELIVERED).
         */
        private fun injectTrackingPixel(html: String, pixelUrl: String): String {
            val pixel =
                """<img src="$pixelUrl" width="1" height="1" alt="" style="display:none;border:0;" />"""
            return if (html.contains("</body>", ignoreCase = true)) {
                html.replace(Regex("</body>", RegexOption.IGNORE_CASE), "$pixel</body>")
            } else {
                html + pixel
            }
        }
    }

}

private inline fun <T> requireExists(block: () -> T): T = try {
    block()
} catch (ex: ResponseStatusException) {
    if (ex.statusCode == HttpStatus.NOT_FOUND)
        throw NonRetryableJobException(ex.reason ?: "Entity not found", ex)
    throw ex
}
