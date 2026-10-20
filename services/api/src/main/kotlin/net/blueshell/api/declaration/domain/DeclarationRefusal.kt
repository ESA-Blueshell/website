package net.blueshell.api.declaration.domain

import net.blueshell.api.declaration.persistence.DeclarationState
import net.blueshell.api.shared.refusal.Refusal
import org.springframework.http.HttpStatus

// A code and the facts, never the sentence: `domains/declarations/refusals.ts` writes that. See api ADR-026.
sealed class DeclarationRefusal(
    status: HttpStatus,
    code: String,
    summary: String,
    facts: Map<String, Any> = emptyMap(),
) : Refusal(status, code, summary, facts)

class DeclarationNotFound : DeclarationRefusal(HttpStatus.NOT_FOUND, "DeclarationNotFound", "There is no such declaration of yours.")

class DeclarationNotDraft : DeclarationRefusal(HttpStatus.CONFLICT, "DeclarationNotDraft", "Only a draft is changed, submitted or deleted.")

class DeclarationNotSubmitted :
    DeclarationRefusal(HttpStatus.CONFLICT, "DeclarationNotSubmitted", "Only a declaration not yet approved is withdrawn.")

/** The declaration moved on before the board acted: approving asks a submitted one, sending back or rejecting one not yet paid. */
class DeclarationStateChanged(
    state: DeclarationState,
) : DeclarationRefusal(
        HttpStatus.CONFLICT,
        "DeclarationStateChanged",
        "That declaration is no longer waiting for this.",
        mapOf("state" to state.name),
    )

/** Sending back and rejecting both tell the submitter why. */
class ReasonMissing : DeclarationRefusal(HttpStatus.BAD_REQUEST, "ReasonMissing", "Sending back or rejecting gives a reason.")

class NoBankAccount : DeclarationRefusal(HttpStatus.BAD_REQUEST, "NoBankAccount", "A declaration is paid to a bank account on file.")

class NoReceipt : DeclarationRefusal(HttpStatus.BAD_REQUEST, "NoReceipt", "A declaration holds at least one receipt.")

class NotConfirmed : DeclarationRefusal(HttpStatus.BAD_REQUEST, "NotConfirmed", "The submitter states the declaration is correct.")

class NoExpense : DeclarationRefusal(HttpStatus.BAD_REQUEST, "NoExpense", "A declaration holds at least one expense.")

/** [field] is `paidOn`, `amount` or `description`; [expense] counts from 1, as the form does. */
class ExpenseIncomplete(
    expense: Int,
    field: String,
) : DeclarationRefusal(
        HttpStatus.BAD_REQUEST,
        "ExpenseIncomplete",
        "An expense misses its day, amount or description.",
        mapOf("expense" to expense, "field" to field),
    )

class PaidInFuture(
    expense: Int,
) : DeclarationRefusal(HttpStatus.BAD_REQUEST, "PaidInFuture", "An expense is paid on or before today.", mapOf("expense" to expense))

class UnknownBooking :
    DeclarationRefusal(HttpStatus.BAD_REQUEST, "UnknownBooking", "An expense names a committee or event that does not exist.")

class UnknownReceipt : DeclarationRefusal(HttpStatus.BAD_REQUEST, "UnknownReceipt", "A receipt is one the submitter uploaded.")

class ReceiptNotFound : DeclarationRefusal(HttpStatus.NOT_FOUND, "ReceiptNotFound", "There is no such receipt you may open.")
