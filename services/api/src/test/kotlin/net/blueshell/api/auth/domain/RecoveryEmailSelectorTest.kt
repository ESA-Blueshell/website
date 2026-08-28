package net.blueshell.api.auth.domain

import net.blueshell.api.user.persistence.User
import net.blueshell.api.shared.enums.TokenPurpose
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * The selector is the single place a recovery purpose becomes an email, so preview and
 * send cannot drift. These pin which template each purpose gets and that the one purpose
 * that must never be mailed still cannot be.
 */
class RecoveryEmailSelectorTest {

    private val frontendUrl = "https://esa-blueshell.nl"

    private fun recipient() = User(
        username = "alice",
        email = "alice@example.com",
        password = "hash",
        initials = "A",
        firstName = "Alice",
        lastName = "Regular",
    )

    @Test
    fun `user activation links to the user activation page`() {
        val email = buildRecoveryEmail(TokenPurpose.USER_ACTIVATION, recipient(), "raw-token", frontendUrl)

        assertThat(email.subject).isEqualTo("Activate your Account")
        assertThat(email.markdownContent).contains("$frontendUrl/account/activate/user#token=raw-token")
        assertThat(email.replyToOverride).isNull()
    }

    @Test
    fun `member activation links to the member page and replies to the board`() {
        val email = buildRecoveryEmail(TokenPurpose.MEMBER_ACTIVATION, recipient(), "raw-token", frontendUrl)

        assertThat(email.markdownContent).contains("$frontendUrl/account/activate/member#token=raw-token")
        assertThat(email.replyToOverride).isEqualTo("board@blueshell.utwente.nl")
    }

    @Test
    fun `password reset links to the reset page`() {
        val email = buildRecoveryEmail(TokenPurpose.PASSWORD_RESET, recipient(), "raw-token", frontendUrl)

        assertThat(email.subject).isEqualTo("Reset Your Blueshell Account Password")
        assertThat(email.markdownContent).contains("$frontendUrl/account/reset-password#token=raw-token")
    }

    @Test
    fun `a signup continuation token is never turned into an email`() {
        assertThatThrownBy {
            buildRecoveryEmail(TokenPurpose.SIGNUP_CONTINUATION, recipient(), "raw-token", frontendUrl)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("must never be emailed")
    }

    @Test
    fun `the placeholder is not mistakable for a token`() {
        // A preview renders this in place of a credential, so it has to read as inert.
        assertThat(PREVIEW_TOKEN_PLACEHOLDER).isEqualTo("PREVIEW-ONLY-NO-TOKEN-ISSUED")
    }

    @Test
    fun `the recipient is addressed by name whichever email is built`() {
        TokenPurpose.entries
            .filter { it != TokenPurpose.SIGNUP_CONTINUATION }
            .forEach { purpose ->
                val email = buildRecoveryEmail(purpose, recipient(), "raw-token", frontendUrl)
                assertThat(email.recipientEmail).isEqualTo("alice@example.com")
                assertThat(email.markdownContent).contains("Alice Regular")
            }
    }
}
