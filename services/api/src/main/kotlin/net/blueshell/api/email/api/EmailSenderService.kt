package net.blueshell.api.email.api

import net.blueshell.api.email.domain.EmailService
import net.blueshell.api.email.domain.EmailTemplateService
import net.blueshell.api.email.domain.EmailTransportClient
import net.blueshell.api.email.domain.SendingAddresses
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.util.PersonalDetails
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class EmailSenderService(
    private val templateService: EmailTemplateService,
    private val emailClient: EmailTransportClient,
    private val emailService: EmailService,
    private val sendingAddresses: SendingAddresses,
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
    fun send(
        emailContent: EmailContent,
        emailType: String,
        jobExecutionId: Long? = null,
    ) {
        val htmlContent = renderEmailHtml(emailContent)

        val outbox = emailService.forSend(emailContent, emailType, jobExecutionId)
        val trackedHtml =
            outbox.trackingToken
                ?.let { token -> injectTrackingPixel(htmlContent, "$appUrl/track/email/open/$token") }
                ?: htmlContent

        try {
            // The site's own mail goes out from the default address where one is marked, else from configuration.
            // An address, or its login, that cannot be read fails this email only, as any transport failure does.
            val sending = emailContent.sendingAddressId?.let(sendingAddresses::routeFor) ?: sendingAddresses.defaultRoute()
            outbox.senderAddress = sending?.address ?: senderAddress
            val messageId =
                emailClient.send(
                    emailContent.recipientEmail,
                    emailContent.recipientName,
                    emailContent.subject,
                    trackedHtml,
                    emailContent.senderNameOverride ?: sending?.displayName ?: senderName,
                    sending?.address ?: senderAddress,
                    emailContent.replyToOverride ?: defaultReplyTo,
                    emailContent.threadHeaders,
                    sending?.route,
                )
            log.info("Sent email id={} type={}", outbox.id, emailType)
            emailService.markSent(outbox, messageId)
        } catch (e: Exception) {
            log.error("Failed to send email id={} type={}", outbox.id, emailType, e)
            emailService.markFailed(outbox, e.javaClass.simpleName, PersonalDetails.scrub(e.message ?: "Send error"))
            throw IllegalStateException("Failed to send email", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(EmailSenderService::class.java)

        /**
         * Injects a 1x1 tracking pixel before </body>.
         * When email clients load remote images the pixel fires GET /track/email/open/{token},
         * which marks the outbox entry as OPENED (and infers DELIVERED).
         */
        private fun injectTrackingPixel(
            html: String,
            pixelUrl: String,
        ): String {
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
