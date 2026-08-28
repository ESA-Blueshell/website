package net.blueshell.api.user.domain

import jakarta.validation.constraints.AssertTrue

/**
 * A new account, validated before it is created. Carries both the uniqueness
 * constraint and the rules that apply only to public registration: a board
 * member creating an account supplies no password and accepts nothing on the
 * applicant's behalf, so each rule passes when [isBoard] is set.
 *
 * The property names are the paths these violations are reported under, so they
 * are part of the error contract.
 */
@UniqueUserCommand
data class UserRegistration(
    val isBoard: Boolean,
    override val username: String,
    override val email: String,
    override val discord: String,
    override val phoneNumber: String,
    val password: String?,
    val consentPrivacy: Boolean,
) : UserUniquenessCandidate {
    override val subjectId: Long? = null

    @get:AssertTrue(message = "Password is required for public user registration.")
    val isPasswordPresentForPublicRegistration: Boolean
        get() = isBoard || !password.isNullOrBlank()

    @get:AssertTrue(message = "Privacy policy consent is required for public user registration.")
    val isPrivacyConsentGivenForPublicRegistration: Boolean
        get() = isBoard || consentPrivacy

    @get:AssertTrue(
        message = "Password must contain at least one lowercase letter, one uppercase letter, one number, and one special character (@$!%*?&)."
    )
    val isPasswordComplexForPublicRegistration: Boolean
        get() = isBoard || (password?.let(PASSWORD_COMPLEXITY_REGEX::matches) == true)

    companion object {
        private val PASSWORD_COMPLEXITY_REGEX =
            Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$")
    }
}
