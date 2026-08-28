package net.blueshell.api.email.api

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.shared.email.EmailContent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

/**
 * The renderer's whole job is to leave the email alone except for the images, which have
 * to survive into a browser that cannot reach the hosted URLs.
 */
class EmailPreviewRendererTest {

    private val emailSender = mockk<EmailSenderService>()
    private val renderer = EmailPreviewRenderer(emailSender)

    private val content = EmailContent(
        recipientEmail = "alice@example.com",
        recipientName = "Alice Regular",
        subject = "Activate your Account",
        markdownContent = "Dear Alice Regular",
    )

    private fun rendersTo(html: String) {
        every { emailSender.renderEmailHtml(content) } returns html
    }

    @Test
    fun `the subject is carried through untouched`() {
        rendersTo("<p>hello</p>")

        assertThat(renderer.render(content).subject).isEqualTo("Activate your Account")
    }

    @Test
    fun `a preview renders through the send path's renderer`() {
        rendersTo("<p>hello</p>")

        renderer.render(content)

        verify(exactly = 1) { emailSender.renderEmailHtml(content) }
    }

    @Test
    fun `the hosted logo and watermark become inline data`() {
        rendersTo(
            """<img src="https://esa-blueshell.nl/img/email/blueshell-logo.png">""" +
                """<td background="https://esa-blueshell.nl/img/email/watermark.png">""",
        )

        val html = renderer.render(content).html

        assertThat(html).doesNotContain("/img/email/blueshell-logo.png")
        assertThat(html).doesNotContain("/img/email/watermark.png")
        assertThat(html).contains("data:image/png;base64,")
    }

    @Test
    fun `a url inside a CSS url() is replaced too`() {
        rendersTo("""<div style="background-image:url(https://esa-blueshell.nl/img/email/watermark.png)"></div>""")

        val html = renderer.render(content).html

        // The delimiter walk has to stop at the opening paren, not swallow it.
        assertThat(html).contains("url(data:image/png;base64,")
        assertThat(html).endsWith(")\"></div>")
    }

    @Test
    fun `every occurrence is replaced, not just the first`() {
        rendersTo(
            """<img src="/img/email/blueshell-logo.png"><img src="/img/email/blueshell-logo.png">""",
        )

        val html = renderer.render(content).html

        assertThat(html).doesNotContain("/img/email/blueshell-logo.png")
        assertThat(html.split("data:image/png;base64,")).hasSize(3)
    }

    @Test
    fun `markup around the images is left exactly as it was`() {
        rendersTo("""<p>before</p><img src="/img/email/watermark.png" alt="x"><p>after</p>""")

        val html = renderer.render(content).html

        assertThat(html).startsWith("<p>before</p><img src=\"data:image/png;base64,")
        assertThat(html).endsWith("\" alt=\"x\"><p>after</p>")
    }

    @Test
    fun `an email with no images is returned unchanged`() {
        rendersTo("<p>no pictures here</p>")

        assertThat(renderer.render(content).html).isEqualTo("<p>no pictures here</p>")
    }

    /**
     * The replacement is one ~180KB token with no URL delimiters in it. A regex that scans
     * back over it backtracks quadratically and turned a preview into minutes of work; this
     * pins the linear behaviour rather than the implementation.
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun `many images stay fast to inline`() {
        rendersTo((1..400).joinToString("") { """<img src="/img/email/watermark.png">""" })

        val html = renderer.render(content).html

        assertThat(html).doesNotContain("/img/email/watermark.png")
    }
}
