package net.blueshell.api.email.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import net.blueshell.api.email.api.EmailPreviewRenderer
import net.blueshell.api.email.persistence.Email
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.model.RenderedEmailPreview
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Reading a sent email back: it renders through the send path's own renderer, and nothing it
 * hands over addresses anywhere.
 */
class SentEmailPreviewServiceTest {

    private val emails = mockk<EmailService>()
    private val renderer = mockk<EmailPreviewRenderer>()
    private val service = SentEmailPreviewService(emails, renderer)

    private fun outboxRow(body: String? = "Dear Alice, welcome.") = Email(
        recipientEmail = "alice@example.com",
        recipientName = "Alice Regular",
        subject = "Welcome to Blueshell",
        bodyMarkdown = body,
        emailType = "welcome",
    )

    private fun rendersTo(html: String) {
        every { renderer.render(any()) } returns RenderedEmailPreview("Welcome to Blueshell", html)
    }

    @Test
    fun `renders the stored body and reports who it went to`() {
        every { emails.findById(7) } returns outboxRow()
        rendersTo("<p>Dear Alice, welcome.</p>")

        val preview = service.preview(7)!!

        assertThat(preview.subject).isEqualTo("Welcome to Blueshell")
        assertThat(preview.html).contains("Dear Alice, welcome.")
        assertThat(preview.recipientEmail).isEqualTo("alice@example.com")
        assertThat(preview.recipientName).isEqualTo("Alice Regular")
    }

    @Test
    fun `renders what the row stored, through the send path's renderer`() {
        every { emails.findById(7) } returns outboxRow()
        val content = slot<EmailContent>()
        every { renderer.render(capture(content)) } returns RenderedEmailPreview("Welcome to Blueshell", "<p>x</p>")

        service.preview(7)

        assertThat(content.captured.markdownContent).isEqualTo("Dear Alice, welcome.")
        assertThat(content.captured.subject).isEqualTo("Welcome to Blueshell")
        assertThat(content.captured.recipientEmail).isEqualTo("alice@example.com")
    }

    @Test
    fun `strips the links out of what it returns`() {
        every { emails.findById(7) } returns outboxRow()
        rendersTo("""<a href="https://esa-blueshell.nl/activate?token=live-token">Activate</a>""")

        val preview = service.preview(7)!!

        assertThat(preview.html).doesNotContain("live-token", "esa-blueshell.nl")
        assertThat(preview.html).contains("Activate")
    }

    @Test
    fun `keeps the images the renderer inlined`() {
        every { emails.findById(7) } returns outboxRow()
        rendersTo("""<img src="data:image/png;base64,AAAB" alt="logo" />""")

        assertThat(service.preview(7)!!.html).contains("data:image/png;base64,AAAB")
    }

    @Test
    fun `has nothing to show for a row stored before bodies were kept`() {
        every { emails.findById(7) } returns outboxRow(body = null)

        assertThat(service.preview(7)).isNull()
    }
}
