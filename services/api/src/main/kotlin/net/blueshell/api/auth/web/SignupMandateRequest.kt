package net.blueshell.api.auth.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import net.blueshell.api.user.web.ACCOUNT_HOLDER_MAX
import net.blueshell.api.user.web.IBAN_MAX
import net.blueshell.api.user.web.WORDING_VERSION_MAX

/** The signup's optional incasso step; the mandate is signed on the day it is sent. */
@Schema(name = "SignupMandateRequest")
data class SignupMandateRequest(
    @field:NotBlank
    @field:Size(max = IBAN_MAX)
    val iban: String,
    @field:NotBlank
    @field:Size(max = ACCOUNT_HOLDER_MAX)
    val accountHolder: String,
    @field:AssertTrue(message = "Authorise the collection to set up incasso.")
    val authorised: Boolean = false,
    /** The version of the authorisation wording the applicant was shown. */
    @field:NotBlank
    @field:Size(max = WORDING_VERSION_MAX)
    val wordingVersion: String = "",
) {
    // A request is logged on a failure; the account number is not.
    override fun toString(): String = "SignupMandateRequest(iban=****${iban.takeLast(SHOWN)})"

    private companion object {
        const val SHOWN = 4
    }
}
