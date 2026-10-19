package net.blueshell.api.declaration.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import net.blueshell.api.declaration.domain.BookingChoice
import net.blueshell.api.declaration.domain.DraftContent
import net.blueshell.api.declaration.domain.ExpenseContent
import net.blueshell.api.declaration.persistence.BookingKind
import net.blueshell.api.declaration.persistence.DeclarationExpense
import net.blueshell.api.declaration.persistence.DeclarationStep
import java.time.LocalDate

private const val EXPENSES_MAX = 50
private const val RECEIPTS_MAX = 50

// A hundred thousand euros, far above anything a member pays out of pocket.
private const val AMOUNT_CENTS_MAX = 10_000_000L

/** A draft as the form holds it; saving does not ask it to be complete, submitting does. */
@Schema(name = "DeclarationDraftRequest")
data class DeclarationDraftRequest(
    @field:Valid
    @field:Size(max = EXPENSES_MAX)
    val expenses: List<ExpenseRequest> = emptyList(),
    @field:Schema(description = "The receipts for the declaration as a whole, as the ids the upload answered.")
    @field:Size(max = RECEIPTS_MAX)
    val receipts: List<Long> = emptyList(),
    val confirmed: Boolean = false,
) {
    fun asContent() = DraftContent(expenses.map { it.asContent() }, receipts, confirmed)
}

@Schema(name = "ExpenseRequest")
data class ExpenseRequest(
    val paidOn: LocalDate? = null,
    @field:Size(max = DeclarationExpense.DESCRIPTION_MAX)
    val description: String = "",
    @field:Min(0)
    @field:Max(AMOUNT_CENTS_MAX)
    val amountCents: Int? = null,
    @field:Valid
    val bookedTo: BookingRequest? = null,
    @field:Size(max = RECEIPTS_MAX)
    val receipts: List<Long> = emptyList(),
) {
    fun asContent() = ExpenseContent(paidOn, description, amountCents, bookedTo?.let { BookingChoice(it.kind, it.id) }, receipts)
}

@Schema(name = "BookingRequest")
data class BookingRequest(
    val kind: BookingKind,
    val id: Long,
)

/** Why the board sends a declaration back or rejects it; the submitter is emailed it as written. */
@Schema(name = "DeclarationReasonRequest")
data class DeclarationReasonRequest(
    @field:NotBlank
    @field:Size(max = DeclarationStep.REASON_MAX)
    val reason: String = "",
)
