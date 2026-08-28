package net.blueshell.api.auth.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import net.blueshell.api.email.api.EmailPreviewRenderer
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.shared.model.RenderedEmailPreview
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/**
 * A preview must be exactly a render: no token issued, nothing recorded, nothing sent.
 * These assert the absences, because an absence is what makes it safe to open.
 */
class RecoveryEmailPreviewServiceTest {

    private val users = mockk<UserService>()
    private val renderer = mockk<EmailPreviewRenderer>()
    private val service = RecoveryEmailPreviewService(users, renderer, "https://esa-blueshell.nl")

    private val alice = User(
        username = "alice",
        email = "alice@example.com",
        password = "hash",
        initials = "A",
        firstName = "Alice",
        lastName = "Regular",
    )

    /** Capture what was handed to the renderer, and echo the markdown back as the body. */
    private fun capturingRenderer(): io.mockk.CapturingSlot<EmailContent> {
        val captured = slot<EmailContent>()
        every { renderer.render(capture(captured)) } answers {
            RenderedEmailPreview(captured.captured.subject, "<html>" + captured.captured.markdownContent + "</html>")
        }
        return captured
    }

    @Test
    fun `the link carries the placeholder rather than a token`() {
        every { users.findById(7L) } returns alice
        capturingRenderer()

        val preview = service.preview(7L, TokenPurpose.USER_ACTIVATION)

        assertThat(preview.html).contains("PREVIEW-ONLY-NO-TOKEN-ISSUED")
        assertThat(preview.linkPlaceholder).isEqualTo("PREVIEW-ONLY-NO-TOKEN-ISSUED")
        assertThat(preview.subject).isEqualTo("Activate your Account")
        assertThat(preview.purpose).isEqualTo(TokenPurpose.USER_ACTIVATION)
        assertThat(preview.recipientEmail).isEqualTo("alice@example.com")
        assertThat(preview.recipientName).isEqualTo("Alice Regular")
    }

    @Test
    fun `each purpose is built as its own email`() {
        every { users.findById(7L) } returns alice
        val captured = capturingRenderer()

        service.preview(7L, TokenPurpose.MEMBER_ACTIVATION)
        assertThat(captured.captured.markdownContent).contains("/account/activate/member#token=")

        service.preview(7L, TokenPurpose.PASSWORD_RESET)
        assertThat(captured.captured.markdownContent).contains("/account/reset-password#token=")
    }

    @Test
    fun `the email goes through the shared preview renderer`() {
        every { users.findById(7L) } returns alice
        capturingRenderer()

        // Which is what inlines the images, so a preview is not full of broken ones.
        val preview = service.preview(7L, TokenPurpose.USER_ACTIVATION)

        assertThat(preview.html).startsWith("<html>")
    }

    @Test
    fun `a signup continuation email cannot be previewed either`() {
        every { users.findById(7L) } returns alice

        assertThatThrownBy { service.preview(7L, TokenPurpose.SIGNUP_CONTINUATION) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `a missing user is reported as not found rather than rendered`() {
        every { users.findById(99L) } throws ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")

        assertThatThrownBy { service.preview(99L, TokenPurpose.USER_ACTIVATION) }
            .isInstanceOf(ResponseStatusException::class.java)
    }
}
