package net.blueshell.api.platform.integration.email

import jakarta.mail.internet.InternetAddress
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * SMTP email client using Spring's JavaMailSender (blueshell.utwente.nl relay).
 *
 * Sends multipart (plain text + HTML) emails for better deliverability — plain-text
 * alternatives reduce spam scores and render correctly in all clients.
 * Sets an explicit Message-ID so outbox records can be correlated to sent messages.
 *
 * Inactive in test/dev profiles — MockSmtpEmailClient takes over there.
 */
@Component
@Profile("!test & !dev")
class SmtpEmailClient(
    private val mailSender: JavaMailSender,
) : EmailTransportClient {

    override fun send(
        toEmail: String,
        toName: String,
        subject: String,
        htmlContent: String,
        senderName: String,
        senderAddress: String,
        replyToAddress: String,
    ): String {
        val message = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, true, "UTF-8")

        helper.setFrom(InternetAddress(senderAddress, senderName, "UTF-8"))
        helper.setTo(InternetAddress(toEmail, toName, "UTF-8"))
        helper.setReplyTo(replyToAddress)
        helper.setSubject(subject)

        // Multipart: plain-text fallback improves deliverability and spam scoring
        helper.setText(toPlainText(htmlContent), htmlContent)

        // Explicit Message-ID anchored to our sending domain for traceability
        val domain = senderAddress.substringAfter("@").ifEmpty { "blueshell.utwente.nl" }
        val messageId = "<${UUID.randomUUID()}@$domain>"
        message.setHeader("Message-ID", messageId)

        mailSender.send(message)
        log.info("Sent email to={} subject='{}' messageId={}", toEmail, subject, messageId)
        return messageId
    }

    companion object {
        private val log = LoggerFactory.getLogger(SmtpEmailClient::class.java)

        private fun toPlainText(html: String): String =
            html.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("<p[^>]*>", RegexOption.IGNORE_CASE), "\n\n")
                .replace(Regex("<[^>]+>"), "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace(Regex("[ \t]+"), " ")
                .replace(Regex("(\n[ \t]*){3,}"), "\n\n")
                .trim()
    }
}
