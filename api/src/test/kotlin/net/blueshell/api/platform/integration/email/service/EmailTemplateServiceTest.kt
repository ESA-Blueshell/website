package net.blueshell.api.platform.integration.email.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * Integration tests for EmailTemplateService.
 *
 * Tests markdown rendering and template processing.
 */
@SpringBootTest
@ActiveProfiles("test")
class EmailTemplateServiceTest {

    @Autowired
    private lateinit var emailTemplateService: EmailTemplateService

    @Test
    fun `createEmail converts markdown to HTML`() {
        // Given: Markdown content
        val markdownContent = """
            # Welcome

            This is **bold** text and *italic* text.

            - List item 1
            - List item 2
        """.trimIndent()

        // When: Creating email
        val html = emailTemplateService.createEmail(
            recipientEmail = "test@example.com",
            recipientName = "Test User",
            mainTitle = "Test Email",
            markdownContent = markdownContent
        )

        // Then: HTML is generated
        assertThat(html)
            .contains("<!DOCTYPE html>", "<html")
            .contains("<h1>Welcome</h1>")
            .contains("<strong>bold</strong>")
            .contains("<em>italic</em>")
            .contains("<ul>", "<li>")
    }

    @Test
    fun `createEmail includes recipient information`() {
        // Given: Simple markdown
        val markdownContent = "Hello World"

        // When: Creating email
        val html = emailTemplateService.createEmail(
            recipientEmail = "john@example.com",
            recipientName = "John Doe",
            mainTitle = "Welcome Email",
            markdownContent = markdownContent
        )

        // Then: Recipient info is included
        assertThat(html)
            .contains("john@example.com")
            .contains("John Doe")
            .contains("Welcome Email")
    }

    @Test
    fun `createEmail handles markdown links`() {
        // Given: Markdown with links
        val markdownContent = """
            Click [here](https://example.com) to continue.

            Visit our [website](https://blueshell.com) for more info.
        """.trimIndent()

        // When: Creating email
        val html = emailTemplateService.createEmail(
            recipientEmail = "test@example.com",
            recipientName = "Test",
            mainTitle = "Test",
            markdownContent = markdownContent
        )

        // Then: Links are converted to HTML
        assertThat(html)
            .contains("<a href=\"https://example.com\">here</a>")
            .contains("<a href=\"https://blueshell.com\">website</a>")
    }

    @Test
    fun `createEmail handles markdown tables`() {
        // Given: Markdown with table
        val markdownContent = """
            | Fee Type | Amount |
            |----------|--------|
            | Half Year| €25.00 |
            | Full Year| €45.00 |
        """.trimIndent()

        // When: Creating email
        val html = emailTemplateService.createEmail(
            recipientEmail = "test@example.com",
            recipientName = "Test",
            mainTitle = "Test",
            markdownContent = markdownContent
        )

        // Then: Table is rendered
        assertThat(html)
            .contains("<table>")
            .contains("<thead>", "<tbody>")
            .contains("<th>", "<td>")
            .contains("Fee Type", "Amount")
            .contains("Half Year", "Full Year")
    }

    @Test
    fun `createEmail handles special characters`() {
        // Given: Markdown with special characters
        val markdownContent = """
            Prices: €25.00 & £20.00 < $30.00

            Special: "quotes" and 'apostrophes'
        """.trimIndent()

        // When: Creating email
        val html = emailTemplateService.createEmail(
            recipientEmail = "test@example.com",
            recipientName = "Test",
            mainTitle = "Test",
            markdownContent = markdownContent
        )

        // Then: Special characters are preserved/escaped correctly
        assertThat(html)
            .contains("€25.00")
            .contains("&amp;", "&lt;")
            .contains("quotes")
    }

    @Test
    fun `createEmail handles multiline paragraphs`() {
        // Given: Multiple paragraphs
        val markdownContent = """
            First paragraph with some text.

            Second paragraph with more text.

            Third paragraph.
        """.trimIndent()

        // When: Creating email
        val html = emailTemplateService.createEmail(
            recipientEmail = "test@example.com",
            recipientName = "Test",
            mainTitle = "Test",
            markdownContent = markdownContent
        )

        // Then: Paragraphs are separated
        assertThat(html)
            .contains("<p>First paragraph")
            .contains("<p>Second paragraph")
            .contains("<p>Third paragraph")
    }

    @Test
    fun `createEmail is well-formed HTML`() {
        // Given: Any markdown
        val markdownContent = "Test email content"

        // When: Creating email
        val html = emailTemplateService.createEmail(
            recipientEmail = "test@example.com",
            recipientName = "Test",
            mainTitle = "Test",
            markdownContent = markdownContent
        )

        // Then: HTML is well-formed
        assertThat(html)
            .startsWith("<!DOCTYPE html>")
            .contains("<html", "<head>", "<body>")
            .contains("</body>", "</html>")
            .contains("charset")
    }
}
