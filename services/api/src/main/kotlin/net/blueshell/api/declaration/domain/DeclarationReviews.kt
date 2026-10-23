package net.blueshell.api.declaration.domain

import net.blueshell.api.declaration.persistence.Declaration
import net.blueshell.api.declaration.persistence.DeclarationRepository
import net.blueshell.api.declaration.persistence.DeclarationState
import net.blueshell.api.declaration.persistence.DeclarationStep
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.user.api.BankAccountLookup
import net.blueshell.api.user.api.BankAccountToPay
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

/** A draft is seen only by the person writing it, one sent back included; anything past it is the board's to see too. */
val Declaration.seenByBoard: Boolean
    get() = state != DeclarationState.DRAFT

/**
 * The board's side of declarations: it sees what was submitted and what came of it, and
 * approves, sends back, rejects or marks it paid. A board member may act on a declaration of their own.
 */
@Service
class DeclarationReviews(
    private val declarations: DeclarationRepository,
    private val jobs: JobQueue,
    private val bankAccounts: BankAccountLookup,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun all(): List<Declaration> = declarations.findAllByStateNotOrderByCreatedAtDescIdDesc(DeclarationState.DRAFT)

    @Transactional(readOnly = true)
    fun one(id: Long): Declaration = declarations.findById(id).orElse(null)?.takeIf { it.seenByBoard } ?: throw DeclarationNotFound()

    @Transactional
    fun approve(
        id: Long,
        by: Long,
    ): Declaration = move(one(id), setOf(DeclarationState.SUBMITTED), DeclarationState.APPROVED, by)

    /** Back to a draft with [reason], which the submitter is emailed; the statement is made again on the next submit. */
    @Transactional
    fun sendBack(
        id: Long,
        by: Long,
        reason: String,
    ): Declaration =
        move(one(id), WITH_THE_BOARD, DeclarationState.DRAFT, by, reason).also {
            it.confirmed = false
            tell(it, DeclarationJobs.Outcome.SENT_BACK)
        }

    /** Final: a rejected declaration is not submitted again. The submitter is emailed [reason]. */
    @Transactional
    fun reject(
        id: Long,
        by: Long,
        reason: String,
    ): Declaration =
        move(one(id), WITH_THE_BOARD, DeclarationState.REJECTED, by, reason).also {
            tell(it, DeclarationJobs.Outcome.REJECTED)
        }

    /** The submitter's account on file now, which a payout goes to. */
    @Transactional(readOnly = true)
    fun bankAccountOf(id: Long): BankAccountToPay? = bankAccounts.toPay(one(id).userId)

    /**
     * The board transferred the money by hand; it went to the account on file now, which keeps its
     * preview and counts as a use of that account (api ADR-041). The submitter is emailed.
     */
    @Transactional
    fun markPaid(
        id: Long,
        by: Long,
    ): Declaration {
        val declaration = one(id)
        // Checked before the payout is counted as a use of the account, not only inside move.
        // TWIN: `payable` in the frontend's Declarations pages.
        if (declaration.state != DeclarationState.APPROVED) throw DeclarationStateChanged(declaration.state)
        val stored = bankAccounts.recordPaidTo(declaration.userId, LocalDate.now(clock))?.stored ?: throw SubmitterHasNoBankAccount()
        move(declaration, setOf(DeclarationState.APPROVED), DeclarationState.PAID, by, paidTo = stored)
        val payload =
            DeclarationJobs.DeclarationPaidPayload(
                declarationId = declaration.id!!,
                submitterId = declaration.userId,
                title = titleOf(declaration),
                totalCents = totalCentsOf(declaration),
                paidTo = stored,
            )
        jobs.runAsync(DeclarationJobs.DeclarationPaid, payload, JobTrigger.SITE_ACTION)
        return declaration
    }

    private fun move(
        declaration: Declaration,
        from: Set<DeclarationState>,
        to: DeclarationState,
        by: Long,
        reason: String? = null,
        paidTo: String? = null,
    ): Declaration {
        if (declaration.state !in from) throw DeclarationStateChanged(declaration.state)
        val said = reason?.trim()?.ifEmpty { throw ReasonMissing() }
        declaration.state = to
        declaration.steps += DeclarationStep(to, clock.instant(), by, said, paidTo)
        return declaration
    }

    // The email quotes the declaration as the board saw it, whatever the submitter changes before it goes.
    private fun tell(
        declaration: Declaration,
        outcome: DeclarationJobs.Outcome,
    ) {
        val payload =
            DeclarationJobs.DeclarationReturnedPayload(
                declarationId = declaration.id!!,
                submitterId = declaration.userId,
                outcome = outcome,
                reason = requireNotNull(declaration.steps.last().reason),
                title = titleOf(declaration),
                totalCents = totalCentsOf(declaration),
            )
        jobs.runAsync(DeclarationJobs.DeclarationReturned, payload, JobTrigger.SITE_ACTION)
    }

    private companion object {
        // TWIN: `withTheBoard` and the bulk actions of the frontend's Declarations pages.
        val WITH_THE_BOARD = setOf(DeclarationState.SUBMITTED, DeclarationState.APPROVED)
    }
}

private fun totalCentsOf(declaration: Declaration): Int = declaration.expenses.sumOf { it.amountCents ?: 0 }

// TWIN: `titleOf` in the frontend's `domains/declarations/declarations.ts` names a declaration the same way.
internal fun titleOf(declaration: Declaration): String {
    val first =
        declaration.expenses
            .firstOrNull()
            ?.description
            ?.trim()
            .orEmpty()
            .ifEmpty { return "New declaration" }
    val more = declaration.expenses.size - 1
    return if (more == 0) first else "$first and $more more"
}
