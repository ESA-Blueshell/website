package net.blueshell.api.email.api

import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.email.domain.EmailTransportClient
import net.blueshell.api.email.domain.EmailService
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.shared.job.NonRetryableJobException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import net.blueshell.api.email.domain.EmailTemplateService

@Service
class EmailSenderService(
    private val templateService: EmailTemplateService,
    private val emailClient: EmailTransportClient,
    private val emailService: EmailService,
    @param:Value($$"${frontend.url}") private val frontendUrl: String,
    @param:Value($$"${app.url}") private val appUrl: String,
    @param:Value($$"${email.from.name}") private val senderName: String,
    @param:Value($$"${email.from.address}") private val senderAddress: String,
    @param:Value($$"${email.reply-to}") private val defaultReplyTo: String,
) {
    /**
     * Render an email to the HTML a recipient would receive, without delivering it. The
     * send path renders through here too, so a preview cannot show something else.
     */
    fun renderEmailHtml(emailContent: EmailContent): String =
        templateService.createEmail(
            emailContent.recipientEmail,
            emailContent.recipientName,
            emailContent.subject,
            emailContent.markdownContent,
        )

    /**
     * Render template, inject tracking pixel, create the outbox record, then hand off
     * to the transport. This is the email module's surface: a caller composes the
     * content it wants sent, and this decides how sending happens.
     */
    fun send(emailContent: EmailContent, emailType: String, jobExecutionId: Long? = null) {
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
