package net.blueshell.api.user.domain

import net.blueshell.api.user.api.PasswordPolicy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * These rules are the only thing standing between the public signup route and a
 * weak password: `CreateUserRequest` carries no password constraint, because the
 * board sends the same request with the password left empty.
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
        fun `demands a password of the allowed length`() {
            fun allowed(length: Int) =
                registration(password = "Aa1!".padEnd(length, 'x')).isPasswordLengthAllowedForPublicRegistration

            assertThat(allowed(PasswordPolicy.MIN_LENGTH - 1)).isFalse()
            assertThat(allowed(PasswordPolicy.MIN_LENGTH)).isTrue()
            assertThat(allowed(PasswordPolicy.MAX_LENGTH)).isTrue()
            assertThat(allowed(PasswordPolicy.MAX_LENGTH + 1)).isFalse()
        }

        @Test
        fun `leaves a missing password to the presence rule`() {
            assertThat(registration(password = null).isPasswordLengthAllowedForPublicRegistration).isTrue()
        }

        /**
         * The form that collects the password asks for a lowercase letter, an
         * uppercase letter, a number and a special character, and says nothing
         * about which symbols count. Refusing a symbol it never warned about is a
         * rejection the applicant cannot act on.
         */
        @Test
        fun `counts any non-alphanumeric as the special character`() {
            for (candidate in listOf("Passw0rd#", "Passw0rd-", "Passw0rd_", "Passw0rd ", "Passw0rd\u00e9")) {
                assertThat(registration(password = candidate).isPasswordComplexForPublicRegistration)
                    .describedAs(candidate)
                    .isTrue()
            }
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
        fun `waives every rule, since the board supplies no password and consents to nothing`() {
            val boardCreated = registration(isBoard = true, password = null, consentPrivacy = false)

            assertThat(boardCreated.isPasswordPresentForPublicRegistration).isTrue()
            assertThat(boardCreated.isPasswordLengthAllowedForPublicRegistration).isTrue()
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
        discordId = "1144058844004233369",
        phoneNumber = "0612345678",
        password = password,
        consentPrivacy = consentPrivacy,
    )
}
