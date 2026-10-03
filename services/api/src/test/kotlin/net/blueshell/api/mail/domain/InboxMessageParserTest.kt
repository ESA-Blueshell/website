package net.blueshell.api.mail.domain

import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Date
import java.util.Properties

class InboxMessageParserTest {
    private val session = Session.getInstance(Properties())
    private val now = Instant.parse("2026-10-01T10:00:00Z")

    private fun message(build: MimeMessage.() -> Unit): MimeMessage =
        MimeMessage(session).apply {
            setFrom(InternetAddress("Lars.Mulder@Example.com", "Lars Mulder"))
            setRecipient(Message.RecipientType.TO, InternetAddress("board@esa-blueshell.nl"))
            subject = "Re: Your contribution"
            setText("I paid yesterday.")
            build()
            saveChanges()
        }

    @Test
    fun `reads a reply with its thread, sender, address and body`() {
        val reply =
            message {
                setHeader("In-Reply-To", "<sent-9@blueshell>")
                setHeader("References", "<sent-1@blueshell> <sent-9@blueshell>")
                setHeader("Delivered-To", "Partners@esa-blueshell.nl")
                sentDate = Date.from(Instant.parse("2026-09-29T11:20:00Z"))
            }.also { it.setHeader("Message-ID", "<reply-1@example.com>") }

        val parsed = InboxMessageParser.parse(reply, "<fallback>", now)!!

        assertThat(parsed.messageId).isEqualTo("<reply-1@example.com>")
        assertThat(parsed.threadIds).containsExactly("<sent-9@blueshell>", "<sent-1@blueshell>")
        assertThat(parsed.fromAddress).isEqualTo("lars.mulder@example.com")
        assertThat(parsed.fromName).isEqualTo("Lars Mulder")
        assertThat(parsed.toAddress).isEqualTo("partners@esa-blueshell.nl")
        assertThat(parsed.bodyText).isEqualTo("I paid yesterday.")
        assertThat(parsed.receivedAt).isEqualTo(Instant.parse("2026-09-29T11:20:00Z"))
        assertThat(parsed.automatic).isFalse()
    }

    @Test
    fun `keeps an automatic reply marked as one, reads both bodies, and stands a fallback in for a missing id`() {
        val out =
            message {
                setHeader("Auto-Submitted", "auto-replied")
                setContent(
                    MimeMultipart("alternative").apply {
                        addBodyPart(MimeBodyPart().apply { setText("Away until Monday") })
                        addBodyPart(MimeBodyPart().apply { setContent("<p>Away until Monday</p>", "text/html") })
                    },
                )
            }
        out.removeHeader("Message-ID")

        val parsed = InboxMessageParser.parse(out, "<uid-1-7@inbox.local>", now)!!

        assertThat(parsed.automatic).isTrue()
        assertThat(parsed.bodyText).isEqualTo("Away until Monday")
        assertThat(parsed.bodyHtml).isEqualTo("<p>Away until Monday</p>")
        assertThat(parsed.messageId).isEqualTo("<uid-1-7@inbox.local>")
        assertThat(parsed.toAddress).isEqualTo("board@esa-blueshell.nl")
        assertThat(InboxMessageParser.parse(message { subject = "Automatic reply: out of office" }, "<x>", now)!!.automatic).isTrue()
        assertThat(InboxMessageParser.parse(message { setHeader("Precedence", "bulk") }, "<x>", now)!!.automatic).isTrue()
        assertThat(InboxMessageParser.parse(message { setHeader("X-Autoreply", "yes") }, "<x>", now)!!.automatic).isTrue()
        assertThat(InboxMessageParser.parse(message { setHeader("Auto-Submitted", "no") }, "<x>", now)!!.automatic).isFalse()
    }

    @Test
    fun `leaves a delivery report to the bounce poller, and a message without a sender`() {
        val report =
            message { setContent(MimeMultipart("report").apply { addBodyPart(MimeBodyPart().apply { setText("Delivery failed") }) }) }
        val daemon = message { setFrom(InternetAddress("MAILER-DAEMON@mail.example.com")) }
        val nobody =
            MimeMessage(session).apply {
                setText("?")
                saveChanges()
            }

        assertThat(InboxMessageParser.parse(report, "<x>", now)).isNull()
        assertThat(InboxMessageParser.parse(daemon, "<x>", now)).isNull()
        assertThat(InboxMessageParser.parse(nobody, "<x>", now)).isNull()
    }
}
