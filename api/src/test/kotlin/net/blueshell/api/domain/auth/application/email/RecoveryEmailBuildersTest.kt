package net.blueshell.api.domain.auth.application.email

import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.enums.Role
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests for recovery email builders.
 *
 * Verifies that EmailContent DTOs are created correctly (ADR-019, ADR-022).
 * These builders serve as Anti-Corruption Layer between domain and platform.
 */
class RecoveryEmailBuildersTest {

    private val frontendUrl = "https://test-frontend.com"
    private val appUrl = "https://test-app.com"

    @Test
    fun `createPasswordResetEmail builds correct EmailContent`() {
        // Given: User and token
        val user = createTestUser("john.doe", "john.doe@example.com", "John Doe")
        val token = "test-token-123"

        // When: Building password reset email
        val emailContent = createPasswordResetEmail(user, token, frontendUrl, appUrl)

        // Then: EmailContent has correct fields
        assertThat(emailContent.recipientEmail).isEqualTo(user.email)
        assertThat(emailContent.recipientName).isEqualTo(user.fullName)
        assertThat(emailContent.subject).isEqualTo("Reset Your Blueshell Account Password")

        // And: Body contains reset link
        assertThat(emailContent.markdownContent)
            .contains("/account/reset-password?username=")
            .contains("token=")
            .contains(user.fullName)
    }

    @Test
    fun `createUserActivationEmail builds correct EmailContent`() {
        // Given: User and token
        val user = createTestUser("jane.smith", "jane.smith@example.com", "Jane Smith")
        val token = "activation-token-456"

        // When: Building user activation email
        val emailContent = createUserActivationEmail(user, token, frontendUrl, appUrl)

        // Then: EmailContent has correct fields
        assertThat(emailContent.recipientEmail).isEqualTo(user.email)
        assertThat(emailContent.recipientName).isEqualTo(user.fullName)
        assertThat(emailContent.subject).isEqualTo("Activate your Account")

        // And: Body contains activation link
        assertThat(emailContent.markdownContent)
            .contains("/account/activate/user?username=")
            .contains("token=")
            .contains(user.fullName)
            .contains("Thank you for signing up")
    }

    @Test
    fun `createMemberActivationEmail builds correct EmailContent`() {
        // Given: User and token
        val user = createTestUser("board.member", "board.member@example.com", "Board Member")
        val token = "member-token-789"

        // When: Building member activation email
        val emailContent = createMemberActivationEmail(user, token, frontendUrl, appUrl)

        // Then: EmailContent has correct fields
        assertThat(emailContent.recipientEmail).isEqualTo(user.email)
        assertThat(emailContent.recipientName).isEqualTo(user.fullName)
        assertThat(emailContent.subject).isEqualTo("Activate your Account")
        assertThat(emailContent.replyTo).isEqualTo("board@blueshell.utwente.nl")

        // And: Body contains member-specific activation link
        assertThat(emailContent.markdownContent)
            .contains("/account/activate/member?token=")
            .contains("board of Blueshell has created an account")
            .contains(user.fullName)
    }

    @Test
    fun `email builders URL-encode username and token correctly`() {
        // Given: User with special characters in username
        val user = createTestUser("user+test@special", "test@example.com", "Test User")
        val token = "token with spaces & special=chars"

        // When: Building emails
        val passwordReset = createPasswordResetEmail(user, token, frontendUrl, appUrl)
        val userActivation = createUserActivationEmail(user, token, frontendUrl, appUrl)

        // Then: URLs are properly encoded
        assertThat(passwordReset.markdownContent)
            .doesNotContain("user+test@special") // Should be URL encoded
            .contains("username=")

        assertThat(userActivation.markdownContent)
            .doesNotContain("token with spaces") // Should be URL encoded
            .contains("token=")
    }

    private fun createTestUser(username: String, email: String, fullName: String): User {
        val names = fullName.split(" ", limit = 2)
        return User(
            username = username,
            email = email,
            password = "dummy",
            initials = names.mapNotNull { it.firstOrNull()?.toString() }.joinToString(""),
            firstName = names.getOrElse(0) { "Test" },
            lastName = names.getOrElse(1) { "User" },
            prefix = "",
            phoneNumber = "0612345678",
            discord = "$username#0001",
            steamid = null,
            newsletter = false,
            enabled = true,
            consentPrivacy = false,
            consentGdpr = false,
            roles = mutableSetOf(Role.MEMBER),
            contactId = null
        )
    }
}
