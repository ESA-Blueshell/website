package net.blueshell.api.auth.domain

import java.time.LocalDate

/**
 * A signup as it stands, for a tab picking the form back up.
 *
 * Here rather than in the kernel: `auth` is the only module that builds or reads one, and the
 * shared-kernel rule counts fan-in (ADR-003). Shaped like the requests the applicant sends back
 * rather than like the account, so nothing the form cannot submit travels. Assembled while the
 * account is still managed, since `open-in-view` is off and the address is lazy.
 */
data class SignupResume(
    val userId: Long,
    val email: String,
    val username: String,
    val initials: String,
    val firstName: String,
    val prefix: String?,
    val lastName: String,
    val discord: String?,
    val phoneNumber: String?,
    val newsletter: Boolean,
    val photoConsent: Boolean,
    /** The confirmation link has been opened, which is what enables the account. */
    val emailConfirmed: Boolean,
    /** The conditions were agreed to, which is not retractable. */
    val conditionsAccepted: Boolean,
    val memberProfile: SignupResumeProfile?,
    val address: SignupResumeAddress?,
)

data class SignupResumeProfile(
    val dateOfBirth: LocalDate?,
    val studentNumber: String?,
    val gender: String?,
    val nationality: String?,
    val bhv: Boolean,
    val ehbo: Boolean,
    val nameOnRosters: Boolean,
)

data class SignupResumeAddress(
    val country: String?,
    val city: String?,
    val street: String?,
    val houseNumber: String?,
    val zipCode: String?,
)
