package net.blueshell.api.platform.integration.email.service

import net.blueshell.api.platform.integration.email.application.service.EmailPreviewRenderer
import net.blueshell.api.platform.integration.email.application.service.EmailSenderService
import net.blueshell.api.platform.integration.email.application.service.EmailUrlRedaction
import net.blueshell.api.shared.email.EmailContent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * The redaction against a real email rather than a hand-written fragment.
 *
 * The unit tests say what the redactor does to markup handed to it. This says what happens
 * to an actual activation email, rendered through the real template with its real chrome:
 * the token the recipient was given does not survive into what an operator reads, and the
 * images the preview inlined still do.
 */
@SpringBootTest
@ActiveProfiles("test")
class SentEmailPreviewRedactionIT {

    @Autowired
    private lateinit var renderer: EmailPreviewRenderer

    @Autowired
    private lateinit var emailSender: EmailSenderService

    private val body = """
        Dear Alice,

        Activate your account through [this link](https://esa-blueshell.nl/activate?token=live-secret-token).

        Or paste it: https://esa-blueshell.nl/activate?token=live-secret-token

        Kind regards,
        The Board
    """.trimIndent()

    private val content = EmailContent(
        recipientEmail = "alice@example.com",
        recipientName = "Alice Regular",
        subject = "Activate your account",
        markdownContent = body,
    )

    @Test
    fun `no url from a real rendered email survives the redaction`() {
        val rendered = renderer.render(content)
        // The email really did carry the token, both as a link and as text.
        assertThat(rendered.html).contains("live-secret-token")

        val redacted = EmailUrlRedaction.redact(rendered.html)

        assertThat(redacted)
            .doesNotContain("live-secret-token")
            .doesNotContain("http://")
            .doesNotContain("https://")
            .doesNotContain("mailto:")
    }

    @Test
    fun `nothing left in the document points anywhere`() {
        val redacted = EmailUrlRedaction.redact(renderer.render(content).html)

        // Every link in the real template — the footer's included — keeps its markup and
        // loses its target. What a link *said* is left alone, which is why the footer's own
        // label survives as text while the address it pointed at does not.
        assertThat(hrefValues(redacted)).isNotEmpty().allMatch(String::isEmpty)
        assertThat(srcValues(redacted)).allMatch { it.isEmpty() || it.startsWith("data:") }
    }

    private fun hrefValues(html: String): List<String> =
        Regex("""href="([^"]*)"""").findAll(html).map { it.groupValues[1] }.toList()

    private fun srcValues(html: String): List<String> =
        Regex("""src="([^"]*)"""").findAll(html).map { it.groupValues[1] }.toList()

    @Test
    fun `the email still reads as it was written`() {
        val redacted = EmailUrlRedaction.redact(renderer.render(content).html)

        assertThat(redacted).contains("Dear Alice", "this link", "The Board")
        // The footer's link text is a domain, and a label is not a credential.
        assertThat(redacted).contains("esa-blueshell.nl")
    }

    @Test
    fun `the images the preview inlined survive`() {
        val rendered = renderer.render(content)
        // Guards the ordering: inline the classpath assets first, redact after, or the logo
        // goes out with the urls.
        assertThat(rendered.html).contains("data:image/png;base64,")

        assertThat(EmailUrlRedaction.redact(rendered.html)).contains("data:image/png;base64,")
    }

    @Test
    fun `the send path is left alone, so a recipient still gets working links`() {
        val sent = emailSender.renderEmailHtml(content)

        assertThat(sent).contains("https://esa-blueshell.nl/activate?token=live-secret-token")
    }
}
