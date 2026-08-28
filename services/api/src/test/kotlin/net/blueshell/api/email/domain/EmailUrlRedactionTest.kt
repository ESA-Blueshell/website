package net.blueshell.api.email.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis

class EmailUrlRedactionTest {

    @Test
    fun `strips the target from a link and keeps what it said`() {
        val html = """<a href="https://esa-blueshell.nl/activate?token=abc123">Activate your account</a>"""

        val redacted = EmailUrlRedaction.redact(html)

        assertThat(redacted).doesNotContain("abc123", "esa-blueshell.nl")
        assertThat(redacted).contains("Activate your account")
        assertThat(redacted).contains("""href=""""")
    }

    @Test
    fun `strips a bare url used as its own link text`() {
        val html = """<p>Reset here: https://esa-blueshell.nl/reset?token=secret-token</p>"""

        val redacted = EmailUrlRedaction.redact(html)

        assertThat(redacted).doesNotContain("secret-token")
        assertThat(redacted).contains(EmailUrlRedaction.PLACEHOLDER)
    }

    @Test
    fun `strips a remote image so reading the outbox announces nothing`() {
        val html = """<img src="https://track.example.com/pixel/abc" width="1" />"""

        val redacted = EmailUrlRedaction.redact(html)

        assertThat(redacted).doesNotContain("track.example.com")
        assertThat(redacted).contains("""width="1"""")
    }

    @Test
    fun `keeps the images the preview inlined`() {
        val html = """<img src="data:image/png;base64,AAAB" alt="logo" />"""

        assertThat(EmailUrlRedaction.redact(html)).isEqualTo(html)
    }

    @Test
    fun `keeps an in-page anchor, which reaches nothing`() {
        val html = """<a href="#top">Back to top</a>"""

        assertThat(EmailUrlRedaction.redact(html)).isEqualTo(html)
    }

    @Test
    fun `strips a mailto, which is still a recipient the reader should not mail by accident`() {
        val html = """<a href="mailto:treasurer@esa-blueshell.nl">Contact the treasurer</a>"""

        val redacted = EmailUrlRedaction.redact(html)

        assertThat(redacted).doesNotContain("treasurer@esa-blueshell.nl")
        assertThat(redacted).contains("Contact the treasurer")
    }

    @Test
    fun `strips a background image referenced from css`() {
        val html = """<td style="background-image: url('https://esa-blueshell.nl/img/email/watermark.png')">x</td>"""

        val redacted = EmailUrlRedaction.redact(html)

        assertThat(redacted).doesNotContain("esa-blueshell.nl", "url(")
        // `none` rather than an empty `url()`: valid css that fetches nothing.
        assertThat(redacted).isEqualTo("""<td style="background-image: none">x</td>""")
    }

    @Test
    fun `leaves a css data uri alone`() {
        val html = """<td style="background-image: url('data:image/png;base64,AAAB')">x</td>"""

        assertThat(EmailUrlRedaction.redact(html)).isEqualTo(html)
    }

    @Test
    fun `strips every url in a document, not only the first`() {
        val html = """
            <a href="https://a.example/one">one</a>
            <a href="https://b.example/two">two</a>
            <img src="https://c.example/three.png" />
        """.trimIndent()

        val redacted = EmailUrlRedaction.redact(html)

        assertThat(redacted).doesNotContain("a.example", "b.example", "c.example")
    }

    @Test
    fun `handles single-quoted attributes`() {
        val html = """<a href='https://esa-blueshell.nl/x?token=q'>go</a>"""

        assertThat(EmailUrlRedaction.redact(html)).doesNotContain("token=q")
    }

    @Test
    fun `stays fast on a document carrying inlined images`() {
        // The preview renderer inlines the logo and the watermark as base64, which puts a few
        // hundred kilobytes of delimiter-free text in front of the patterns. A pattern that
        // can backtrack over that turns a preview into a hang, so this is a real guard.
        val inlined = """<img src="data:image/png;base64,${"A".repeat(400_000)}" />"""
        val html = "$inlined<a href=\"https://esa-blueshell.nl/activate?token=abc\">go</a>$inlined"

        val elapsed = measureTimeMillis {
            val redacted = EmailUrlRedaction.redact(html)
            assertThat(redacted).doesNotContain("token=abc")
        }

        assertThat(elapsed).isLessThan(2_000)
    }
}
