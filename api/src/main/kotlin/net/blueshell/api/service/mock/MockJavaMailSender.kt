package net.blueshell.api.service.mock

import jakarta.mail.Address
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.mail.MailException
import org.springframework.mail.MailPreparationException
import org.springframework.mail.MailSendException
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessagePreparator
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Test double for JavaMailSender capturing outbox for assertions.
 */
@Component
@Primary
@Profile("test | dev")
class MockJavaMailSender : JavaMailSender {
    private val session: Session = Session.getInstance(Properties())

    val outbox: MutableList<MimeMessage?> = CopyOnWriteArrayList<MimeMessage?>()

    val simpleOutbox: MutableList<SimpleMailMessage?> = CopyOnWriteArrayList<SimpleMailMessage?>()

    override fun createMimeMessage(): MimeMessage {
        return MimeMessage(session)
    }

    @Throws(MailException::class)
    override fun createMimeMessage(contentStream: InputStream): MimeMessage {
        try {
            return MimeMessage(session, contentStream)
        } catch (e: Exception) {
            throw MailSendException("Failed to create MimeMessage from stream", e)
        }
    }

    @Throws(MailException::class)
    override fun send(mimeMessage: MimeMessage) {
        outbox.add(cloneMessage(mimeMessage))
        MockJavaMailSender.log.info(
            "[mail-mock] captured email: subject='{}' to={}",
            safeSubject(mimeMessage),
            safeRecipients(mimeMessage)
        )
    }

    @Throws(MailException::class)
    override fun send(vararg mimeMessages: MimeMessage) {
        for (m in mimeMessages) send(m)
    }

    @Throws(MailException::class)
    override fun send(mimeMessagePreparator: MimeMessagePreparator) {
        val m = createMimeMessage()
        try {
            mimeMessagePreparator.prepare(m)
        } catch (e: Exception) {
            throw MailPreparationException(e)
        }
        send(m)
    }

    @Throws(MailException::class)
    override fun send(vararg mimeMessagePreparators: MimeMessagePreparator) {
        for (p in mimeMessagePreparators) send(p)
    }

    /**
     * Clear outbox between tests.
     */
    fun clear() {
        outbox.clear()
    }

    private fun cloneMessage(original: MimeMessage): MimeMessage {
        try {
            ByteArrayOutputStream().use { bos ->
                original.saveChanges()
                original.writeTo(bos)
                ByteArrayInputStream(bos.toByteArray()).use { bis ->
                    return MimeMessage(session, bis)
                }
            }
        } catch (e: Exception) {
            throw MailSendException("Failed to clone MimeMessage", e)
        }
    }

    @Throws(MailException::class)
    override fun send(simpleMessage: SimpleMailMessage) {
        simpleOutbox.add(SimpleMailMessage(simpleMessage))
        MockJavaMailSender.log.info(
            "[mail-mock] captured simple email: subject='{}' to={}",
            simpleMessage.subject,
            simpleMessage.to.contentToString()
        )
    }

    @Throws(MailException::class)
    override fun send(vararg simpleMessages: SimpleMailMessage) {
        for (sm in simpleMessages) {
            send(sm)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(MockJavaMailSender::class.java)

        private fun safeSubject(m: MimeMessage): String? {
            try {
                return m.subject
            } catch (ignored: Exception) {
                return "<n/a>"
            }
        }

        private fun safeRecipients(m: MimeMessage): MutableList<String?> {
            try {
                return Arrays.stream<Address?>(
                    Objects.requireNonNullElse<Array<Address?>?>(
                        m.allRecipients,
                        arrayOfNulls<Address>(0)
                    )
                )
                    .map<String?> { obj: Address? -> obj.toString() }.toList()
            } catch (ignored: Exception) {
                return mutableListOf<String?>()
            }
        }
    }
}
