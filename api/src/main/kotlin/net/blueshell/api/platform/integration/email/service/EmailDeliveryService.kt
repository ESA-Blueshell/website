package net.blueshell.api.platform.integration.email.service

import jakarta.mail.MessagingException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ClassPathResource
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import java.io.UnsupportedEncodingException

@Service
class EmailDeliveryService @Autowired constructor(private val mailSender: JavaMailSender) {
    /**
     * Sends an HTML email (synchronous). Jobs wrap retries/async.
     */
    fun sendHtmlEmail(
        toEmail: String,
        subject: String,
        htmlContent: String,
        senderName: String,
        senderAddress: String
    ) {
        try {
            val message = mailSender.createMimeMessage()
            val helper = MimeMessageHelper(message, true, "UTF-8")

            helper.setFrom(senderAddress, senderName)
            helper.setTo(toEmail)
            helper.setSubject(subject)
            helper.setText(htmlContent, true)

            // keep your inline assets
            helper.addInline("logo", ClassPathResource("templates/assets/BSLOGO.png"), "image/png")
            helper.addInline("bg", ClassPathResource("templates/assets/BackdropBlack.png"), "image/png")

            mailSender.send(message)
            log.info("Sent email to {} from {} with subject {}", toEmail, senderAddress, subject)
        } catch (e: MessagingException) {
            log.error(
                "Failed to send email to {} with subject {}: {}",
                toEmail,
                subject,
                e.message,
                e
            )
            throw RuntimeException("Failed to send email", e)
        } catch (e: UnsupportedEncodingException) {
            log.error(
                "Failed to send email to {} with subject {}: {}",
                toEmail,
                subject,
                e.message,
                e
            )
            throw RuntimeException("Failed to send email", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(EmailDeliveryService::class.java)
    }
}
