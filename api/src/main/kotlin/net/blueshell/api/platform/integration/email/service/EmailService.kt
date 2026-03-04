package net.blueshell.api.platform.integration.email.service

import net.blueshell.api.domain.auth.application.email.createMemberActivationEmail
import net.blueshell.api.domain.auth.application.email.createPasswordResetEmail
import net.blueshell.api.domain.auth.application.email.createUserActivationEmail
import net.blueshell.api.domain.contribution.application.ContributionReminderService
import net.blueshell.api.domain.contribution.application.email.createContributionReminderEmail
import net.blueshell.api.domain.contribution.persistence.ContributionReminder
import net.blueshell.api.domain.event.application.EventSignUpService
import net.blueshell.api.domain.event.application.email.createEventSignupEmail
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.platform.integration.email.application.service.EmailOutboxService
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.enums.ResetType
import net.blueshell.api.shared.job.NonRetryableJobException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class EmailService(
    private val templateService: EmailTemplateService,
    private val mailDelivery: EmailDeliveryService,
    private val users: UserService,
    private val reminders: ContributionReminderService,
    private val eventSignUps: EventSignUpService,
    private val emailOutboxService: EmailOutboxService,
    @param:Value($$"${frontend.url}") private val frontendUrl: String,
    @param:Value($$"${app.url}") private val appUrl: String
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

    fun sendUserResetEmail(userId: Long, token: String, resetType: ResetType, jobExecutionId: Long? = null) {
        val user = requireExists { users.findById(userId) }
        log.info("Sending {} email for user={}", resetType, userId)

        val emailContent = when (resetType) {
            ResetType.MEMBER_ACTIVATION -> createMemberActivationEmail(user, token, frontendUrl)
            ResetType.USER_ACTIVATION -> createUserActivationEmail(user, token, frontendUrl)
            ResetType.PASSWORD_RESET -> createPasswordResetEmail(user, token, frontendUrl)
        }

        deliver(emailContent, "email.recovery", jobExecutionId)
    }

    /**
     * Render via template service, inject tracking pixel, create outbox record, and send via delivery service.
     */
    private fun deliver(emailContent: EmailContent, emailType: String, jobExecutionId: Long? = null) {
        val htmlContent = templateService.createEmail(
            emailContent.recipientEmail,
            emailContent.recipientName,
            emailContent.subject,
            emailContent.markdownContent
        )

        val outbox = emailOutboxService.createPending(emailContent, emailType, jobExecutionId)
        val trackedHtml = outbox.trackingToken
            ?.let { token -> injectTrackingPixel(htmlContent, "$appUrl/track/email/open/$token") }
            ?: htmlContent

        try {
            val messageId = mailDelivery.sendHtmlEmail(
                emailContent.recipientEmail,
                emailContent.recipientName,
                emailContent.subject,
                trackedHtml,
                emailContent.senderName,
                emailContent.senderAddress,
                emailContent.replyTo
            )
            emailOutboxService.markSent(outbox, messageId)
        } catch (e: Exception) {
            emailOutboxService.markFailed(outbox, e.javaClass.simpleName, e.message ?: "Send error")
            throw e
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(EmailService::class.java)

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
