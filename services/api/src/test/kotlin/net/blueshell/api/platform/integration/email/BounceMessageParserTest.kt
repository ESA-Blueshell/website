package net.blueshell.api.platform.integration.email

import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import net.blueshell.api.platform.integration.email.adapter.BounceMessageParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Properties

class BounceMessageParserTest {

    private val session: Session = Session.getInstance(Properties())

    @Test
    fun `parses a Postfix-style DSN by Original-Message-ID`() {
        val dsn = mimeFromText(
            """
            From: MAILER-DAEMON@example.com
            To: bounce@blueshell.local
            Subject: Undelivered Mail Returned to Sender
            Content-Type: multipart/report; report-type=delivery-status; boundary="b"

            --b
            Content-Type: text/plain

            Delivery failed for <alice@example.com>.

            --b
            Content-Type: message/delivery-status

            Reporting-MTA: dns; relay.example.com
            Original-Recipient: rfc822;alice@example.com
            Final-Recipient: rfc822; alice@example.com
            Action: failed
            Status: 5.1.1
            Diagnostic-Code: smtp; 550 5.1.1 User unknown
            Original-Message-ID: <abc-123@blueshell.local>

            --b--
            """.trimIndent(),
        )

        val parsed = BounceMessageParser.parse(dsn)
        assertThat(parsed).isNotNull
        parsed!!
        assertThat(parsed.originalMessageId).isEqualTo("<abc-123@blueshell.local>")
        assertThat(parsed.recipient).isEqualTo("alice@example.com")
        assertThat(parsed.action).isEqualTo("failed")
        assertThat(parsed.status).isEqualTo("5.1.1")
        assertThat(parsed.diagnostic).contains("550 5.1.1 User unknown")
    }

    @Test
    fun `falls back to attached message Message-ID when Original-Message-ID is missing`() {
        val dsn = mimeFromText(
            """
            From: MAILER-DAEMON@example.com
            To: bounce@blueshell.local
            Subject: Undelivered Mail Returned to Sender
            Content-Type: multipart/report; report-type=delivery-status; boundary="b"

            --b
            Content-Type: text/plain

            Bounced.

            --b
            Content-Type: message/delivery-status

            Final-Recipient: rfc822;bob@example.com
            Action: failed
            Status: 5.1.1

            --b
            Content-Type: text/rfc822-headers

            Message-ID: <fallback-789@blueshell.local>
            Subject: Original

            --b--
            """.trimIndent(),
        )

        val parsed = BounceMessageParser.parse(dsn)
        assertThat(parsed).isNotNull
        assertThat(parsed!!.originalMessageId).isEqualTo("<fallback-789@blueshell.local>")
    }

    @Test
    fun `returns null for non-DSN messages`() {
        val regular = mimeFromText(
            """
            From: a@example.com
            To: b@example.com
            Subject: hi
            Content-Type: text/plain

            Nothing to see.
            """.trimIndent(),
        )
        assertThat(BounceMessageParser.parse(regular)).isNull()
    }

    @Test
    fun `returns null for a DSN with neither Original-Message-ID nor attached message`() {
        val dsn = mimeFromText(
            """
            From: MAILER-DAEMON@example.com
            To: bounce@blueshell.local
            Subject: bounce
            Content-Type: multipart/report; report-type=delivery-status; boundary="b"

            --b
            Content-Type: text/plain

            Bounced.

            --b
            Content-Type: message/delivery-status

            Final-Recipient: rfc822;carol@example.com
            Action: failed

            --b--
            """.trimIndent(),
        )
        assertThat(BounceMessageParser.parse(dsn)).isNull()
    }

    private fun mimeFromText(text: String): MimeMessage =
        MimeMessage(session, text.replace("\n", "\r\n").byteInputStream(Charsets.UTF_8))
}
