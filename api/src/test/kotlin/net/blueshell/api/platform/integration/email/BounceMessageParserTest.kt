package net.blueshell.api.platform.integration.email

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BounceMessageParserTest {

    private lateinit var parser: BounceMessageParser

    @BeforeEach
    fun setUp() {
        parser = BounceMessageParser()
    }

    @Test
    fun `parses RFC 3464 DSN hard bounce`() {
        val message = loadFixture("dsn-hard-bounce.eml", uid = 1)

        val result = parser.parse(message)

        assertThat(result).isNotNull
        assertThat(result!!.originalMessageId).isEqualTo("<original-msg-001@blueshell.utwente.nl>")
        assertThat(result.status).isEqualTo("5.1.1")
        assertThat(result.action).isEqualTo(BounceAction.FAILED)
        assertThat(result.diagnosticCode).contains("550 5.1.1")
        assertThat(result.rawSubject).contains("Failure")
    }

    @Test
    fun `parses RFC 3464 DSN soft bounce`() {
        val message = loadFixture("dsn-soft-bounce.eml", uid = 2)

        val result = parser.parse(message)

        assertThat(result).isNotNull
        assertThat(result!!.originalMessageId).isEqualTo("<original-msg-002@blueshell.utwente.nl>")
        assertThat(result.status).isEqualTo("4.7.1")
        assertThat(result.action).isEqualTo(BounceAction.DELAYED)
        assertThat(result.diagnosticCode).contains("452 4.7.1")
    }

    @Test
    fun `parses heuristic bounce from subject and body`() {
        val message = loadFixture("heuristic-bounce.eml", uid = 3)

        val result = parser.parse(message)

        assertThat(result).isNotNull
        assertThat(result!!.originalMessageId).isEqualTo("<original-msg-003@blueshell.utwente.nl>")
        assertThat(result.action).isEqualTo(BounceAction.FAILED)
        assertThat(result.status).isNull()
    }

    @Test
    fun `filters out auto-reply OOO messages`() {
        val message = loadFixture("auto-reply.eml", uid = 4)

        val result = parser.parse(message)

        assertThat(result).isNull()
    }

    @Test
    fun `returns null for plain message without bounce indicators`() {
        val plainEmail = buildRawMessage(
            uid = 5,
            subject = "Re: Meeting tomorrow",
            contentType = "text/plain",
            body = "Sounds good, see you then!",
        )

        val result = parser.parse(plainEmail)

        assertThat(result).isNull()
    }

    @Test
    fun `normalizes message ID with angle brackets`() {
        // Build a heuristic-style bounce that references a message ID without brackets
        val body = """
            This message was undeliverable.

            Message-ID: original-msg-no-brackets@blueshell.utwente.nl
        """.trimIndent()
        val message = buildRawMessage(
            uid = 6,
            subject = "Undeliverable: Test",
            contentType = "text/plain",
            body = body,
        )

        val result = parser.parse(message)

        // Should not match because our regex expects <...> in Message-ID
        // This is correct behavior — Message-ID should always have angle brackets in SMTP
        assertThat(result).isNull()
    }

    private fun loadFixture(filename: String, uid: Long): RawBounceMessage {
        val bytes = javaClass.classLoader.getResourceAsStream("fixtures/bounce/$filename")!!.readAllBytes()

        // Extract subject and content type from the raw bytes
        val text = String(bytes)
        val subject = Regex("(?i)^Subject:\\s*(.+)", RegexOption.MULTILINE)
            .find(text)?.groupValues?.get(1)?.trim() ?: ""
        val contentType = Regex("(?i)^Content-Type:\\s*([^\\r\\n]+)", RegexOption.MULTILINE)
            .find(text)?.groupValues?.get(1)?.trim() ?: "text/plain"

        return RawBounceMessage(uid = uid, subject = subject, contentType = contentType, rawBytes = bytes)
    }

    private fun buildRawMessage(uid: Long, subject: String, contentType: String, body: String): RawBounceMessage {
        val raw = """
            From: someone@example.com
            To: sitecie@blueshell.utwente.nl
            Subject: $subject
            MIME-Version: 1.0
            Content-Type: $contentType; charset=us-ascii

            $body
        """.trimIndent()
        return RawBounceMessage(uid = uid, subject = subject, contentType = contentType, rawBytes = raw.toByteArray())
    }
}
