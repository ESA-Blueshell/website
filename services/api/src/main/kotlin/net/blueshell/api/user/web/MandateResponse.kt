package net.blueshell.api.user.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import net.blueshell.api.user.api.AddressFields
import net.blueshell.api.user.domain.IncassoStanding
import net.blueshell.api.user.persistence.MandateKind
import java.time.Instant
import java.time.LocalDate

/** A membership's mandate as any response shows it: the account number masked to its last four. */
@Schema(name = "MandateResponse")
data class MandateResponse(
    val membershipId: Long,
    val standing: IncassoStanding,
    @field:Schema(description = "Null where no mandate is recorded, or where it cannot be opened now.")
    val accountHolder: String?,
    @field:Schema(description = "The IBAN's country code; with the last two, all a response carries of it.")
    val ibanCountry: String?,
    @field:Schema(description = "The IBAN's last two characters.")
    val ibanLastTwo: String?,
    val reference: String?,
    val signedOn: LocalDate?,
    val recordedBy: Long?,
    val recordedAt: Instant?,
    @field:Schema(description = "Online where the member authorised it on the site, paper where the board recorded it.")
    val kind: MandateKind?,
    @field:Schema(description = "The moment an online mandate was authorised.")
    val authorisedAt: Instant?,
)

@Schema(name = "RecordMandateRequest")
data class RecordMandateRequest(
    @field:NotBlank
    val iban: String,
    @field:NotBlank
    val accountHolder: String,
    @field:NotNull
    val signedOn: LocalDate,
    /** The board confirmed that this paper mandate replaces one the member authorised online. */
    val replacesOnline: Boolean = false,
) {
    // A request is logged on a failure; the account number is not.
    override fun toString(): String = "RecordMandateRequest(iban=****${iban.takeLast(SHOWN)}, signedOn=$signedOn)"

    private companion object {
        const val SHOWN = 4
    }
}

/** A mandate's full IBAN, answered to a board member's reveal and to nothing else. */
@Schema(name = "RevealedIbanResponse")
data class RevealedIbanResponse(
    val iban: String,
) {
    // The one response that carries the account number; a log line never does.
    override fun toString(): String = "RevealedIbanResponse(****${iban.takeLast(SHOWN)})"

    private companion object {
        const val SHOWN = 4
    }
}

/** A person's own mandate, the account masked to its last four. */
@Schema(name = "OwnMandateResponse")
data class OwnMandateResponse(
    val standing: IncassoStanding,
    val ibanCountry: String?,
    val ibanLastTwo: String?,
    val reference: String?,
    val signedOn: LocalDate?,
    @field:Schema(description = "Set up before the membership started, and moved onto it once it does.")
    val pending: Boolean,
)

/** The address a member confirms when authorising incasso online. */
@Schema(name = "MandateAddressRequest")
data class MandateAddressRequest(
    @field:NotBlank
    val country: String = "",
    @field:NotBlank
    val city: String = "",
    @field:NotBlank
    val street: String = "",
    @field:NotBlank
    val houseNumber: String = "",
    @field:NotBlank
    val zipCode: String = "",
) {
    fun asFields() = AddressFields(country.trim(), city.trim(), street.trim(), houseNumber.trim(), zipCode.trim())
}

/** A member's own bank details; the mandate is signed on the day they are sent. */
@Schema(name = "SetUpMandateRequest")
data class SetUpMandateRequest(
    @field:NotBlank
    val iban: String,
    @field:NotBlank
    val accountHolder: String,
    @field:AssertTrue(message = "Authorise the collection to set up incasso.")
    val authorised: Boolean = false,
    /** The version of the authorisation wording the member was shown. */
    @field:NotBlank
    val wordingVersion: String = "",
    /** The address the member confirmed, which the mandate keeps as its own record. */
    @field:Valid
    @field:NotNull
    val address: MandateAddressRequest? = null,
) {
    // A request is logged on a failure; the account number is not.
    override fun toString(): String = "SetUpMandateRequest(iban=****${iban.takeLast(SHOWN)})"

    private companion object {
        const val SHOWN = 4
    }
}
