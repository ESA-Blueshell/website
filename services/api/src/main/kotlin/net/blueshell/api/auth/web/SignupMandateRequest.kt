package net.blueshell.api.auth.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank

/** The signup's optional incasso step; the mandate is signed on the day it is sent. */
@Schema(name = "SignupMandateRequest")
data class SignupMandateRequest(
    @field:NotBlank
    val iban: String,
    @field:NotBlank
    val accountHolder: String,
    @field:AssertTrue(message = "Authorise the collection to set up incasso.")
    val authorised: Boolean = false,
) {
    // A request is logged on a failure; the account number is not.
    override fun toString(): String = "SignupMandateRequest(iban=****${iban.takeLast(SHOWN)})"

    private companion object {
        const val SHOWN = 4
    }
}
