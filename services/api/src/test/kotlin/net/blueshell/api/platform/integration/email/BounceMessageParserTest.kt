package net.blueshell.api.platform.integration.email

import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import net.blueshell.api.platform.integration.email.adapter.BounceMessageParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Properties

/**
 * Unit tests for [BounceMessageParser] driven by canned DSN fixtures.
 *
 * The parser is the piece that needs the most coverage: the IMAP polling
 * service itself just shuttles messages through it. Building [MimeMessage]
 * instances directly from a string lets the test assert exactly the bits
 * the SmtpEmailClient cares about (Original-Message-ID correlation,
 * diagnostic text).
 */
class BounceMessageParserTest {

    private val session: Session = Session.getInstance(Properties())

    @Test
    fun `parses Postfix DSN with Original-Message-ID and Diagnostic-Code`() {
        val raw = """
            From: mailer-daemon@example.org
            To: bounce@blueshell.example
            Subject: Undelivered Mail Returned to Sender
            Content-Type: multipart/report; report-type=delivery-status; boundary="bnd"

            --bnd
            Content-Type: text/plain; charset=us-ascii

            Mailbox unavailable.

            --bnd
            Content-Type: message/delivery-status

            Reporting-MTA: dns; example.org
            Original-Message-ID: <abc-123@blueshell.example>
            Final-Recipient: rfc822; nobody@example.org
            Action: failed
            Status: 5.1.1
            Diagnostic-Code: smtp; 550 5.1.1 No such user

            --bnd--
            """.trimIndent().replace("\n", "\r\n")

        val parsed = BounceMessageParser.parse(MimeMessage(session, raw.byteInputStream()))

        assertThat(parsed).isNotNull
        assertThat(parsed!!.originalMessageId).isEqualTo("<abc-123@blueshell.example>")
        assertThat(parsed.recipient).isEqualTo("nobody@example.org")
        assertThat(parsed.action).isEqualTo("failed")
        assertThat(parsed.diagnostic).contains("550")
        assertThat(parsed.describe()).contains("550")
    }

    @Test
    fun `returns null when message is not a DSN`() {
        val raw = """
            From: alice@example.org
            To: bob@example.org
            Subject: just a regular email
            Content-Type: text/plain

            Hello!
            """.trimIndent().replace("\n", "\r\n")

        val parsed = BounceMessageParser.parse(MimeMessage(session, raw.byteInputStream()))

        assertThat(parsed).isNull()
    }

    @Test
    fun `returns null when delivery-status part has no Original-Message-ID and no attached message`() {
        val raw = """
            From: mailer-daemon@example.org
            To: bounce@blueshell.example
            Subject: Undelivered
            Content-Type: multipart/report; report-type=delivery-status; boundary="bnd"

            --bnd
            Content-Type: text/plain

            Mailbox unavailable.

            --bnd
            Content-Type: message/delivery-status

            Reporting-MTA: dns; example.org
            Action: failed
            Status: 5.1.1

            --bnd--
            """.trimIndent().replace("\n", "\r\n")

        val parsed = BounceMessageParser.parse(MimeMessage(session, raw.byteInputStream()))

        assertThat(parsed).isNull()
    }

    @Test
    fun `falls back to attached rfc822-headers Message-ID when delivery-status omits Original-Message-ID`() {
        val raw = """
            From: mailer-daemon@example.org
            To: bounce@blueshell.example
            Subject: Undelivered
            Content-Type: multipart/report; report-type=delivery-status; boundary="bnd"

            --bnd
            Content-Type: text/plain

            Mailbox unavailable.

            --bnd
            Content-Type: message/delivery-status

            Reporting-MTA: dns; example.org
            Final-Recipient: rfc822; nobody@example.org
            Action: failed
            Status: 5.1.1
            Diagnostic-Code: smtp; 550 No such user

            --bnd
            Content-Type: text/rfc822-headers

            From: sender@blueshell.example
            To: nobody@example.org
            Message-ID: <fallback-456@blueshell.example>
            Subject: original

            --bnd--
            """.trimIndent().replace("\n", "\r\n")

        val parsed = BounceMessageParser.parse(MimeMessage(session, raw.byteInputStream()))

        assertThat(parsed).isNotNull
        assertThat(parsed!!.originalMessageId).isEqualTo("<fallback-456@blueshell.example>")
    }
}
