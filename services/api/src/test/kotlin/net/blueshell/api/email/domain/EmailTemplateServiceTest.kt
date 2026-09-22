package net.blueshell.api.email.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import org.thymeleaf.context.IContext

class EmailTemplateServiceTest {
    private val templateEngine = mockk<TemplateEngine>()
    private val service = EmailTemplateService(templateEngine)

    init {
        ReflectionTestUtils.setField(service, "frontendUrl", "https://esa-blueshell.nl")
    }

    private fun contextOf(markdown: String): Context {
        val captured = slot<IContext>()
        every { templateEngine.process("emails/email-template", capture(captured)) } returns "<html></html>"

        service.createEmail(
            recipientEmail = "ferdy@example.com",
            recipientName = "Ferdy de Vries",
            mainTitle = "Welcome to Blueshell Esports",
            markdownContent = markdown,
        )

        return captured.captured as Context
    }

    @Test
    fun `the body arrives as html carrying its own colours`() {
        val emailContent = contextOf("Dear Ferdy").getVariable("emailContent") as String

        assertThat(emailContent).contains("<p style=").contains("color:#FFFFFF")
    }

    @Test
    fun `the recipient and the title reach the template`() {
        val context = contextOf("Dear Ferdy")

        assertThat(context.getVariable("sentTo")).isEqualTo("ferdy@example.com")
        assertThat(context.getVariable("fullName")).isEqualTo("Ferdy de Vries")
        assertThat(context.getVariable("mainTitle")).isEqualTo("Welcome to Blueshell Esports")
        assertThat(context.getVariable("frontendUrl")).isEqualTo("https://esa-blueshell.nl")
    }

    @Test
    fun `the processed template is what comes back`() {
        every { templateEngine.process("emails/email-template", any<IContext>()) } returns "<html>done</html>"

        val html =
            service.createEmail(
                recipientEmail = "ferdy@example.com",
                recipientName = "Ferdy de Vries",
                mainTitle = "Welcome to Blueshell Esports",
                markdownContent = "Dear Ferdy",
            )

        assertThat(html).isEqualTo("<html>done</html>")
    }
}
