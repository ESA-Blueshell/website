package net.blueshell.api.auth.web

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.auth.domain.SignupResume
import java.time.LocalDate

/**
 * Everything a signup needs to carry on in a tab that lost what it was holding.
 *
 * Shaped like the first two steps' requests rather than like the account, so the form
 * puts it straight back into the fields it came out of and sends it back unchanged.
 * The password is absent because it was never readable and is not editable mid-signup,
 * and the two facts at the end are what decide which step the applicant lands on.
 */
@Schema(name = "SignupResumeResponse")
data class SignupResumeResponse(
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
    @Schema(description = "Whether the confirmation link has been opened, which is what enables the account")
    val emailConfirmed: Boolean,
    @Schema(description = "Whether the membership conditions were already agreed to, which is not retractable")
    val conditionsAccepted: Boolean,
    val memberProfile: SignupResumeProfileResponse?,
    val address: SignupResumeAddressResponse?,
)

@Schema(name = "SignupResumeProfileResponse")
data class SignupResumeProfileResponse(
    val dateOfBirth: LocalDate?,
    val studentNumber: String?,
    val gender: String?,
    val nationality: String?,
    val bhv: Boolean,
    val ehbo: Boolean,
    val nameOnRosters: Boolean,
)

@Schema(name = "SignupResumeAddressResponse")
data class SignupResumeAddressResponse(
    val country: String?,
    val city: String?,
    val street: String?,
    val houseNumber: String?,
    val zipCode: String?,
)

fun SignupResume.asResponse(): SignupResumeResponse = SignupResumeResponse(
    userId = userId,
    email = email,
    username = username,
    initials = initials,
    firstName = firstName,
    prefix = prefix,
    lastName = lastName,
    discord = discord,
    phoneNumber = phoneNumber,
    newsletter = newsletter,
    photoConsent = photoConsent,
    emailConfirmed = emailConfirmed,
    conditionsAccepted = conditionsAccepted,
    memberProfile = memberProfile?.let {
        SignupResumeProfileResponse(
            dateOfBirth = it.dateOfBirth,
            studentNumber = it.studentNumber,
            gender = it.gender,
            nationality = it.nationality,
            bhv = it.bhv,
            ehbo = it.ehbo,
            nameOnRosters = it.nameOnRosters,
        )
    },
    address = address?.let {
        SignupResumeAddressResponse(
            country = it.country,
            city = it.city,
            street = it.street,
            houseNumber = it.houseNumber,
            zipCode = it.zipCode,
        )
    },
)
