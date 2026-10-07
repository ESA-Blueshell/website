package net.blueshell.api.user.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import net.blueshell.api.user.api.AddressFields
import net.blueshell.api.user.domain.IncassoStanding
import net.blueshell.api.user.persistence.MandateKind
import java.time.Instant
import java.time.LocalDate

/*
 * What a mandate request may carry at most. A sealed value is longer than what it seals, so these
 * keep one inside its column: an IBAN is 34 characters, written with spaces at most 42, and SEPA
 * and ING's file take 70 characters of account holder.
 */
const val IBAN_MAX = 42
const val ACCOUNT_HOLDER_MAX = 70
const val WORDING_VERSION_MAX = 16
private const val ADDRESS_LINE_MAX = 150
private const val ADDRESS_PART_MAX = 20

/** How a person pays and their mandate as any response shows it: the account number masked to its last four. */
@Schema(name = "MandateResponse")
data class MandateResponse(
    val userId: Long,
    val standing: IncassoStanding,
    @field:Schema(description = "Pays by incasso rather than by transfer, whether or not a mandate is recorded.")
    val incasso: Boolean,
    @field:Schema(description = "Null where no mandate is recorded, or where it cannot be opened now.")
    val accountHolder: String?,
    @field:Schema(description = "The IBAN's country code; with the last two, all a response carries of it.")
    val ibanCountry: String?,
    @field:Schema(description = "The IBAN's last two characters.")
    val ibanLastTwo: String?,
    val reference: String?,
    val signedOn: LocalDate?,
    val recordedBy: Long?,
    @field:Schema(description = "Who recorded it, by name; null where the account is gone.")
    val recordedByName: String?,
    val recordedAt: Instant?,
    @field:Schema(description = "Online where the member authorised it on the site, paper where the board recorded it.")
    val kind: MandateKind?,
    @field:Schema(description = "The moment an online mandate was authorised.")
    val authorisedAt: Instant?,
    @field:Schema(
        description = "True once the sealed bank details were wiped; the reference, signing date and masked IBAN stay as the record.",
    )
    val bankDetailsWiped: Boolean,
)

@Schema(name = "RecordMandateRequest")
data class RecordMandateRequest(
    @field:NotBlank
    @field:Size(max = IBAN_MAX)
    val iban: String,
    @field:NotBlank
    @field:Size(max = ACCOUNT_HOLDER_MAX)
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

/** Whether a person pays by incasso or by transfer. */
@Schema(name = "PaysByRequest")
data class PaysByRequest(
    val incasso: Boolean,
)

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
)

/** The address a member confirms when authorising incasso online. */
@Schema(name = "MandateAddressRequest")
data class MandateAddressRequest(
    @field:NotBlank
    @field:ValidCountryCode
    val country: String = "",
    @field:NotBlank
    @field:Size(max = ADDRESS_LINE_MAX)
    val city: String = "",
    @field:NotBlank
    @field:Size(max = ADDRESS_LINE_MAX)
    val street: String = "",
    @field:NotBlank
    @field:Size(max = ADDRESS_PART_MAX)
    val houseNumber: String = "",
    @field:NotBlank
    @field:Size(max = ADDRESS_PART_MAX)
    val zipCode: String = "",
) {
    fun asFields() = AddressFields(country.trim(), city.trim(), street.trim(), houseNumber.trim(), zipCode.trim())
}

/** A member's own bank details; the mandate is signed on the day they are sent. */
@Schema(name = "SetUpMandateRequest")
data class SetUpMandateRequest(
    @field:NotBlank
    @field:Size(max = IBAN_MAX)
    val iban: String,
    @field:NotBlank
    @field:Size(max = ACCOUNT_HOLDER_MAX)
    val accountHolder: String,
    @field:AssertTrue(message = "Authorise the collection to set up incasso.")
    val authorised: Boolean = false,
    /** The version of the authorisation wording the member was shown. */
    @field:NotBlank
    @field:Size(max = WORDING_VERSION_MAX)
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
