package net.blueshell.api.platform.integration.email.application.service

import net.blueshell.api.domain.auth.application.email.createMemberActivationEmail
import net.blueshell.api.domain.auth.application.email.createPasswordResetEmail
import net.blueshell.api.domain.auth.application.email.createUserActivationEmail
import net.blueshell.api.domain.contribution.application.ContributionReminderService
import net.blueshell.api.domain.contribution.application.IncassoNotificationService
import net.blueshell.api.domain.contribution.application.email.createContributionReminderEmail
import net.blueshell.api.domain.contribution.application.email.createIncassoNotificationEmail
import net.blueshell.api.domain.contribution.domain.service.resolveFeeTypeFromAmount
import net.blueshell.api.domain.contribution.persistence.ContributionReminder
import net.blueshell.api.domain.contribution.persistence.IncassoNotification
import net.blueshell.api.domain.event.application.EventSignUpService
import net.blueshell.api.domain.event.application.email.createEventSignupEmail
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.platform.config.BankProperties
import net.blueshell.api.platform.integration.email.adapter.EmailTransportClient
import net.blueshell.api.platform.integration.email.application.service.EmailService
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.enums.ResetType
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
    private val incassoNotifications: IncassoNotificationService,
    private val eventSignUps: EventSignUpService,
    private val emailService: EmailService,
    private val bank: BankProperties,
    @param:Value($$"${frontend.url}") private val frontendUrl: String,
    @param:Value($$"${app.url}") private val appUrl: String,
    @param:Value($$"${email.from.name}") private val senderName: String,
    @param:Value($$"${email.from.address}") private val senderAddress: String,
    @param:Value($$"${email.reply-to}") private val defaultReplyTo: String,
) {
    fun sendContributionReminderEmail(userId: Long, contributionPeriodId: Long, jobExecutionId: Long? = null) {
        val reminder = requireExists { reminders.findById(ContributionReminder.Id(userId, contributionPeriodId)) }
        val emailContent = if (reminder.amount != null && reminder.paymentDueDate != null) {
            // Bulk reminder: use specific amount and due date. The fee type is not
            // persisted, so recover it from the resolved amount to state the reason.
            createContributionReminderEmail(
                reminder.user,
                reminder.contributionPeriod,
                reminder.amount!!,
                reminder.paymentDueDate!!,
                bank,
                resolveFeeTypeFromAmount(reminder.amount!!, reminder.contributionPeriod)
            )
        } else {
            // Single-user reminder: use all options
            createContributionReminderEmail(
                reminder.user,
                reminder.contributionPeriod,
                bank
            )
        }
        deliver(emailContent, "email.contribution-reminder", jobExecutionId)
    }

    fun sendIncassoNotificationEmail(userId: Long, contributionPeriodId: Long, jobExecutionId: Long? = null) {
        val notification = requireExists { incassoNotifications.findById(IncassoNotification.Id(userId, contributionPeriodId)) }
        val emailContent = createIncassoNotificationEmail(
            notification.user,
            notification.contributionPeriod,
            notification.amount!!,
            notification.expectedIncassoDate!!,
            resolveFeeTypeFromAmount(notification.amount!!, notification.contributionPeriod),
        )
        deliver(emailContent, "email.incasso-notification", jobExecutionId)
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
     * Render an [EmailContent] to its final HTML body exactly as the send path does
     * (Markdown → HTML → Thymeleaf template), WITHOUT persisting an outbox record,
     * injecting a tracking pixel, or transmitting anything. This is the reusable render
     * step shared by [deliver] and the email-preview endpoints, so a preview is faithful
     * to what would actually be sent.
     */
    fun renderEmailHtml(emailContent: EmailContent): String =
        templateService.createEmail(
            emailContent.recipientEmail,
            emailContent.recipientName,
            emailContent.subject,
            emailContent.markdownContent
        )

    /** Render template, inject tracking pixel, create the outbox record, then hand off to the transport. */
    private fun deliver(emailContent: EmailContent, emailType: String, jobExecutionId: Long? = null) {
        val htmlContent = renderEmailHtml(emailContent)

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
