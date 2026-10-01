package net.blueshell.api.email.web

import net.blueshell.api.email.api.EmailPreviewRenderer
import net.blueshell.api.email.api.SiteMarkdownEmails
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.model.RenderedEmailPreview
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class EmailRenderTest {
    @Test
    fun `renders an editor's message as the email it becomes`() {
        val siteMarkdown: SiteMarkdownEmails = mock()
        val renderer: EmailPreviewRenderer = mock()
        whenever(siteMarkdown.forEmail("__hi__")).thenReturn("<u>hi</u>")
        whenever(
            renderer.render(EmailContent("", "Ann", "Hello", "<u>hi</u>")),
        ).thenReturn(RenderedEmailPreview("Hello", "<html>hi</html>"))

        val rendered =
            EmailManagementController(mock(), mock(), mock(), mock(), siteMarkdown, renderer)
                .render(RenderEmailRequest(subject = "Hello", message = "__hi__", recipientName = "Ann"))

        assertThat(rendered).isEqualTo(RenderedEmailDTO("Hello", "<html>hi</html>"))
        assertThat(RenderEmailRequest().recipientName).isEqualTo("Member")
    }
}
