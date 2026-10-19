package net.blueshell.api.declaration.web

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.declaration.domain.Receipts
import net.blueshell.api.declaration.persistence.BookingKind
import net.blueshell.api.declaration.persistence.Declaration
import net.blueshell.api.declaration.persistence.DeclarationState
import net.blueshell.api.file.persistence.File
import net.blueshell.api.user.api.UserService
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate

@Schema(name = "DeclarationResponse")
data class DeclarationResponse(
    val id: Long,
    val state: DeclarationState,
    val submitterId: Long,
    @field:Schema(description = "The submitter's name as it is now.")
    val submitter: String,
    val confirmed: Boolean,
    val createdAt: Instant,
    val expenses: List<ExpenseResponse>,
    @field:Schema(description = "The receipts for the declaration as a whole.")
    val receipts: List<ReceiptResponse>,
    @field:Schema(
        description = "Every change of state, oldest first. A draft step after the first is a withdrawal or, with a reason, a send back.",
    )
    val steps: List<DeclarationStepResponse>,
)

@Schema(name = "ExpenseResponse")
data class ExpenseResponse(
    val id: Long,
    val paidOn: LocalDate?,
    val description: String,
    val amountCents: Int?,
    val bookedTo: BookingResponse?,
    val receipts: List<ReceiptResponse>,
)

@Schema(name = "BookingResponse")
data class BookingResponse(
    val kind: BookingKind,
    val id: Long,
    @field:Schema(description = "The committee's or event's name when the expense was saved.")
    val name: String,
)

@Schema(name = "ReceiptResponse")
data class ReceiptResponse(
    val id: Long,
    val name: String,
    val mediaType: String,
    @field:Schema(description = "Where the submitter and the board open it, as a path on the api.")
    val url: String,
)

@Schema(name = "DeclarationStepResponse")
data class DeclarationStepResponse(
    val state: DeclarationState,
    val at: Instant,
    @field:Schema(description = "Who made the change, by name, or nothing where their account is gone.")
    val by: String?,
    val reason: String?,
)

/** Declarations as a response names them: people by name, receipts by where they are opened. */
@Component
class DeclarationResponses(
    private val users: UserService,
    private val receipts: Receipts,
) {
    fun of(declaration: Declaration): DeclarationResponse = of(listOf(declaration)).single()

    fun of(declarations: List<Declaration>): List<DeclarationResponse> {
        val people = declarations.flatMap { one -> one.steps.mapNotNull { it.byUserId } + one.userId }
        val names = users.findAllByIds(people.distinct()).associate { it.id to it.fullName }
        val files = receipts.describe(declarations.flatMap { it.allReceiptFileIds })

        fun receiptsOf(ids: List<Long>) = ids.mapNotNull { id -> files[id]?.asReceipt(id) }
        return declarations.map { declaration ->
            DeclarationResponse(
                id = declaration.id!!,
                state = declaration.state,
                submitterId = declaration.userId,
                submitter = names[declaration.userId].orEmpty(),
                confirmed = declaration.confirmed,
                createdAt = declaration.createdAt,
                expenses =
                    declaration.expenses.map { expense ->
                        ExpenseResponse(
                            id = expense.id!!,
                            paidOn = expense.paidOn,
                            description = expense.description,
                            amountCents = expense.amountCents,
                            bookedTo = expense.bookedTo?.let { BookingResponse(it.kind, it.id, it.name) },
                            receipts = receiptsOf(expense.receiptFileIds),
                        )
                    },
                receipts = receiptsOf(declaration.receiptFileIds),
                steps = declaration.steps.map { DeclarationStepResponse(it.state, it.at, it.byUserId?.let(names::get), it.reason) },
            )
        }
    }
}

fun File.asReceipt(id: Long) = ReceiptResponse(id, name, mediaType, "/declarations/receipts/$id")
