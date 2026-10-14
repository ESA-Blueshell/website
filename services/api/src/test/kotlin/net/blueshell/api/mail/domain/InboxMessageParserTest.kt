package net.blueshell.api.mail.domain

import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
    fun `leaves a delivery report to the bounce poller, and refuses a message without a usable sender`() {
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
        assertThatThrownBy { InboxMessageParser.parse(nobody, "<x>", now) }.isInstanceOf(UnreadableMessage::class.java)
        val tooLong = message { setFrom(InternetAddress("${"a".repeat(320)}@example.com")) }
        assertThatThrownBy { InboxMessageParser.parse(tooLong, "<x>", now) }.isInstanceOf(UnreadableMessage::class.java)
    }

    @Test
    fun `keeps every field within its column, and a thread to its fifty newest ids`() {
        val sprawling =
            message {
                setFrom(InternetAddress("lars@example.com", "L".repeat(300)))
                subject = "S".repeat(1200)
                setHeader("Delivered-To", "Partners <Partners@esa-blueshell.nl>")
                setHeader("References", (1..80).joinToString(" ") { "<ref-$it@x>" })
                setHeader("In-Reply-To", "<${"r".repeat(600)}@x>")
            }.also { it.setHeader("Message-ID", "<${"m".repeat(600)}@x>") }

        val parsed = InboxMessageParser.parse(sprawling, "<fallback>", now)!!

        assertThat(parsed.fromName).hasSize(255)
        assertThat(parsed.subject).hasSize(998)
        val named = message { setHeader("From", "=?utf-8?Q?Lars=0D=0ABcc=3A_x=40y.z?= <lars@example.com>") }
        assertThat(InboxMessageParser.parse(named, "<x>", now)!!.fromName).isEqualTo("Lars Bcc: x@y.z")
        val spaced = message { setHeader("From", "\"lars mulder\"@example.com") }
        assertThatThrownBy { InboxMessageParser.parse(spaced, "<x>", now) }.isInstanceOf(UnreadableMessage::class.java)
        val broken = message { setHeader("From", "a: b: <c") }
        assertThatThrownBy { InboxMessageParser.parse(broken, "<x>", now) }.isInstanceOf(UnreadableMessage::class.java)
        val folded = message { setSubject("=?utf-8?Q?Hi=0D=0ABcc:_x@y.z?=") }
        assertThat(InboxMessageParser.parse(folded, "<x>", now)!!.subject).isEqualTo("Hi Bcc: x@y.z")
        assertThat(parsed.toAddress).isEqualTo("partners@esa-blueshell.nl")
        assertThat(parsed.messageId).isEqualTo("<fallback>")
        assertThat(parsed.inReplyTo).isNull()
        assertThat(parsed.threadIds).hasSize(50).startsWith("<ref-80@x>").endsWith("<ref-31@x>")
    }

    @Test
    fun `reads a body only up to what is kept, and parts only so deep`() {
        val long = message { setText("x".repeat(200_000)) }
        assertThat(InboxMessageParser.parse(long, "<x>", now)!!.bodyText).hasSize(65_536)

        var nested = MimeMultipart("mixed").apply { addBodyPart(MimeBodyPart().apply { setText("deep") }) }
        // Past the ten levels read, yet shallow enough for Jakarta Mail to build: it recurses too.
        repeat(30) {
            val inner = nested
            nested = MimeMultipart("mixed").apply { addBodyPart(MimeBodyPart().apply { setContent(inner) }) }
        }
        val deep = message { setContent(nested) }
        assertThat(InboxMessageParser.parse(deep, "<x>", now)!!.bodyText).isNull()
    }

    @Test
    fun `reads an out of office by its subject only where the subject says so`() {
        fun automatic(subject: String) = InboxMessageParser.parse(message { this.subject = subject }, "<x>", now)!!.automatic

        assertThat(automatic("Out of office: back on Monday")).isTrue()
        assertThat(automatic("Afwezig: tot maandag")).isTrue()
        assertThat(automatic("Out of office hours, can we move Thursday?")).isFalse()
        assertThat(automatic("Afwezigheid bespreken?")).isFalse()
    }
}
