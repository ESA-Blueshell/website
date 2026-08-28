package net.blueshell.api.user.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * These three rules are the only thing standing between the public signup route
 * and an account with no password: `CreateUserRequest` scopes its password
 * constraints to the `Creation` group, and `SignupController.signUp` validates
 * the default group.
 */
class UserRegistrationTest {

    @Nested
    inner class PublicRegistration {

        @Test
        fun `demands a password`() {
            assertThat(registration(password = null).isPasswordPresentForPublicRegistration).isFalse()
            assertThat(registration(password = "  ").isPasswordPresentForPublicRegistration).isFalse()
            assertThat(registration(password = "Passw0rd!").isPasswordPresentForPublicRegistration).isTrue()
        }

        @Test
        fun `demands a complex password`() {
            assertThat(registration(password = "password").isPasswordComplexForPublicRegistration).isFalse()
            assertThat(registration(password = "Password").isPasswordComplexForPublicRegistration).isFalse()
            assertThat(registration(password = "Password1").isPasswordComplexForPublicRegistration).isFalse()
            assertThat(registration(password = "Password1!").isPasswordComplexForPublicRegistration).isTrue()
        }

        @Test
        fun `demands privacy consent`() {
            assertThat(registration(consentPrivacy = false).isPrivacyConsentGivenForPublicRegistration).isFalse()
            assertThat(registration(consentPrivacy = true).isPrivacyConsentGivenForPublicRegistration).isTrue()
        }

        @Test
        fun `never carries a subject id, so uniqueness is checked against every account`() {
            assertThat(registration().subjectId).isNull()
        }
    }

    @Nested
    inner class BoardCreated {

        @Test
        fun `waives all three, since the board supplies no password and consents to nothing`() {
            val boardCreated = registration(isBoard = true, password = null, consentPrivacy = false)

            assertThat(boardCreated.isPasswordPresentForPublicRegistration).isTrue()
            assertThat(boardCreated.isPasswordComplexForPublicRegistration).isTrue()
            assertThat(boardCreated.isPrivacyConsentGivenForPublicRegistration).isTrue()
        }
    }

    private fun registration(
        isBoard: Boolean = false,
        password: String? = "Passw0rd!",
        consentPrivacy: Boolean = true,
    ) = UserRegistration(
        isBoard = isBoard,
        username = "john",
        email = "john@example.com",
        discord = "john#0001",
        phoneNumber = "0612345678",
        password = password,
        consentPrivacy = consentPrivacy,
    )
}
