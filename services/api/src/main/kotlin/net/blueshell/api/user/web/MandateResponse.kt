package net.blueshell.api.user.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import net.blueshell.api.user.domain.IncassoStanding
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
)

@Schema(name = "RecordMandateRequest")
data class RecordMandateRequest(
    @field:NotBlank
    val iban: String,
    @field:NotBlank
    val accountHolder: String,
    @field:NotNull
    val signedOn: LocalDate,
) {
    // A request is logged on a failure; the account number is not.
    override fun toString(): String = "RecordMandateRequest(iban=****${iban.takeLast(SHOWN)}, signedOn=$signedOn)"

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

/** A member's own bank details; the mandate is signed on the day they are sent. */
@Schema(name = "SetUpMandateRequest")
data class SetUpMandateRequest(
    @field:NotBlank
    val iban: String,
    @field:NotBlank
    val accountHolder: String,
    @field:AssertTrue(message = "Authorise the collection to set up incasso.")
    val authorised: Boolean = false,
) {
    // A request is logged on a failure; the account number is not.
    override fun toString(): String = "SetUpMandateRequest(iban=****${iban.takeLast(SHOWN)})"

    private companion object {
        const val SHOWN = 4
    }
}
