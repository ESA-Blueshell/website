package net.blueshell.api.user.domain

import net.blueshell.api.shared.refusal.Refusal
import org.springframework.http.HttpStatus

// A code and the facts, never the sentence: `domains/user/refusals.ts` writes that. See ADR-026.
sealed class MandateRefusal(
    status: HttpStatus,
    code: String,
    summary: String,
) : Refusal(status, code, summary)

class InvalidIban : MandateRefusal(HttpStatus.BAD_REQUEST, "InvalidIban", "That is not a valid IBAN.")

class MandateSignedInFuture : MandateRefusal(HttpStatus.BAD_REQUEST, "MandateSignedInFuture", "A mandate cannot be signed after today.")

class AccountHolderMissing : MandateRefusal(HttpStatus.BAD_REQUEST, "AccountHolderMissing", "A mandate names the account holder.")
