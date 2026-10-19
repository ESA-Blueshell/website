package net.blueshell.api.declaration.domain

import net.blueshell.api.declaration.persistence.Declaration
import net.blueshell.api.declaration.persistence.DeclarationExpense
import net.blueshell.api.declaration.persistence.DeclarationRepository
import net.blueshell.api.declaration.persistence.DeclarationState
import net.blueshell.api.declaration.persistence.DeclarationStep
import net.blueshell.api.user.api.BankAccountLookup
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** One expense as the submitter wrote it; a draft's may still miss its day, amount or words. */
data class ExpenseContent(
    val paidOn: LocalDate?,
    val description: String,
    val amountCents: Int?,
    val bookedTo: BookingChoice?,
    val receiptFileIds: List<Long>,
)

/** What a draft holds: its expenses, the receipts for the declaration as a whole and the statement. */
data class DraftContent(
    val expenses: List<ExpenseContent>,
    val receiptFileIds: List<Long>,
    val confirmed: Boolean,
)

/**
 * A person's own declarations: written as a draft, submitted to the board and withdrawn until it
 * is approved. Only a draft is changed or deleted, and each change of state is kept as a step.
 */
@Service
class Declarations(
    private val declarations: DeclarationRepository,
    private val receipts: Receipts,
    private val bookings: Bookings,
    private val bankAccounts: BankAccountLookup,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun own(userId: Long): List<Declaration> = declarations.findAllByUserIdOrderByCreatedAtDescIdDesc(userId)

    @Transactional(readOnly = true)
    fun ownOne(
        userId: Long,
        id: Long,
    ): Declaration = declarations.findByIdAndUserId(id, userId) ?: throw DeclarationNotFound()

    @Transactional
    fun start(
        userId: Long,
        content: DraftContent,
    ): Declaration {
        val now = clock.instant()
        val declaration = Declaration(userId, now)
        declaration.steps += DeclarationStep(DeclarationState.DRAFT, now, userId)
        fill(declaration, content)
        return declarations.save(declaration)
    }

    @Transactional
    fun save(
        userId: Long,
        id: Long,
        content: DraftContent,
    ): Declaration {
        val declaration = draftOf(userId, id)
        val held = declaration.allReceiptFileIds
        fill(declaration, content)
        declarations.flush()
        receipts.release(held - declaration.allReceiptFileIds.toSet())
        return declaration
    }

    @Transactional
    fun delete(
        userId: Long,
        id: Long,
    ) {
        val declaration = draftOf(userId, id)
        val held = declaration.allReceiptFileIds
        declarations.delete(declaration)
        declarations.flush()
        receipts.release(held)
    }

    @Transactional
    fun submit(
        userId: Long,
        id: Long,
    ): Declaration {
        val declaration = draftOf(userId, id)
        requireComplete(declaration)
        if (!declaration.confirmed) throw NotConfirmed()
        if (!bankAccounts.isOnFile(userId)) throw NoBankAccount()
        return move(declaration, DeclarationState.SUBMITTED, userId)
    }

    /** Back to a draft, until the board approves it; the statement is made again on the next submit. */
    @Transactional
    fun withdraw(
        userId: Long,
        id: Long,
    ): Declaration {
        val declaration = ownOne(userId, id)
        if (declaration.state != DeclarationState.SUBMITTED) throw DeclarationNotSubmitted()
        declaration.confirmed = false
        return move(declaration, DeclarationState.DRAFT, userId)
    }

    private fun draftOf(
        userId: Long,
        id: Long,
    ): Declaration = ownOne(userId, id).also { if (it.state != DeclarationState.DRAFT) throw DeclarationNotDraft() }

    private fun move(
        declaration: Declaration,
        to: DeclarationState,
        by: Long,
    ): Declaration {
        declaration.state = to
        declaration.steps += DeclarationStep(to, clock.instant(), by)
        return declaration
    }

    private fun fill(
        declaration: Declaration,
        content: DraftContent,
    ) {
        val held = declaration.allReceiptFileIds.toSet()
        val named = (content.receiptFileIds + content.expenses.flatMap { it.receiptFileIds })
        if (named.any { it !in held && !receipts.mayAttach(declaration.userId, it) }) throw UnknownReceipt()
        // A committee or event kept on a draft keeps its name, even once it is gone.
        val booked = declaration.expenses.mapNotNull { it.bookedTo }.associateBy { BookingChoice(it.kind, it.id) }
        declaration.replaceExpenses(
            content.expenses.mapIndexed { index, expense ->
                DeclarationExpense(
                    declaration = declaration,
                    position = index,
                    paidOn = expense.paidOn,
                    description = expense.description.trim(),
                    amountCents = expense.amountCents,
                    bookedTo = expense.bookedTo?.let { booked[it] ?: bookings.resolve(it) },
                ).apply { receiptFileIds += expense.receiptFileIds.distinct() }
            },
        )
        declaration.receiptFileIds.clear()
        declaration.receiptFileIds += content.receiptFileIds.distinct()
        declaration.confirmed = content.confirmed
    }

    // TWIN: the declaration page checks the same before it submits.
    private fun requireComplete(declaration: Declaration) {
        if (declaration.expenses.isEmpty()) throw NoExpense()
        val today = LocalDate.now(clock.withZone(ZONE))
        declaration.expenses.forEachIndexed { index, expense ->
            val number = index + 1
            val paidOn = expense.paidOn ?: throw ExpenseIncomplete(number, "paidOn")
            if (paidOn.isAfter(today)) throw PaidInFuture(number)
            if ((expense.amountCents ?: 0) <= 0) throw ExpenseIncomplete(number, "amount")
            if (expense.description.isBlank()) throw ExpenseIncomplete(number, "description")
        }
        if (declaration.allReceiptFileIds.isEmpty()) throw NoReceipt()
    }

    private companion object {
        // The day a member paid is a day in the Netherlands, not in UTC.
        val ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")
    }
}
