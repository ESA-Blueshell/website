package net.blueshell.api.user.web

import io.swagger.v3.oas.annotations.media.Schema
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
    val accountHolder: String?,
    @field:Schema(description = "The last four characters of the IBAN; no response carries more.")
    val ibanLastFour: String?,
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
