package net.blueshell.api.email.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * A client that strips `<style>` must still find a colour on every element, or its dark theme
 * picks one and the copy disappears into the canvas.
 */
class EmailBodyMarkdownTest {
    @Test
    fun `body copy carries its colour inline`() {
        val html = EmailBodyMarkdown.render("Dear Ferdy")

        assertThat(html).contains("<p style=").contains("color:#FFFFFF")
    }

    @Test
    fun `list items carry their colour inline`() {
        val html = EmailBodyMarkdown.render("- first\n- second")

        assertThat(html).contains("<li style=").contains("color:#FFFFFF")
    }

    @Test
    fun `headings carry the colour of their level`() {
        val html = EmailBodyMarkdown.render("# Title\n\n## Section")

        assertThat(html).contains("<h1 style=").contains("color:#3387FA")
        assertThat(html).contains("<h2 style=")
    }

    @Test
    fun `links carry their colour inline`() {
        val html = EmailBodyMarkdown.render("[pay](https://esa-blueshell.nl/)")

        assertThat(html).contains("<a href=\"https://esa-blueshell.nl/\" style=")
    }

    @Test
    fun `emphasis carries its colour inline`() {
        val html = EmailBodyMarkdown.render("**6 October 2026** and *soon*")

        assertThat(html).contains("<strong style=").contains("<em style=")
    }

    @Test
    fun `table cells carry their colour inline`() {
        val html =
            EmailBodyMarkdown.render(
                """
                | Fee | Amount |
                | --- | ------ |
                | Year | 10.00 |
                """.trimIndent(),
            )

        assertThat(html).contains("<table style=").contains("<th style=").contains("<td style=")
    }

    @Test
    fun `a code block keeps its own background over the block's`() {
        val html = EmailBodyMarkdown.render("```\nIBAN\n```")

        assertThat(html).contains("<pre style=").contains("background-color:transparent")
    }

    @Test
    fun `markdown still renders to html`() {
        assertThat(EmailBodyMarkdown.render("Dear Ferdy")).contains("Dear Ferdy")
    }
}
