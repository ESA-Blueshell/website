package net.blueshell.api.user.domain

import net.blueshell.api.shared.refusal.Refusal
import org.springframework.http.HttpStatus
import java.time.Instant

// A code and the facts, never the sentence: `domains/user/refusals.ts` writes that. See ADR-026.
sealed class MandateRefusal(
    status: HttpStatus,
    code: String,
    summary: String,
    facts: Map<String, Any> = emptyMap(),
) : Refusal(status, code, summary, facts)

class InvalidIban : MandateRefusal(HttpStatus.BAD_REQUEST, "InvalidIban", "That is not a valid IBAN.")

class MandateSignedInFuture : MandateRefusal(HttpStatus.BAD_REQUEST, "MandateSignedInFuture", "A mandate cannot be signed after today.")

class NoMandateRecorded : MandateRefusal(HttpStatus.NOT_FOUND, "NoMandateRecorded", "No mandate is recorded on this membership.")

class AccountHolderMissing : MandateRefusal(HttpStatus.BAD_REQUEST, "AccountHolderMissing", "A mandate names the account holder.")

/** A mandate's sealed bank details do not open for their member: they were changed outside the site. */
class BankDetailsUnopenable(
    cause: Throwable?,
) : MandateRefusal(HttpStatus.CONFLICT, "BankDetailsUnopenable", "A mandate's bank details do not open for their member.") {
    init {
        cause?.let(::initCause)
    }
}

/** An online mandate needs the address the member confirmed, with every part filled in. */
class MandateAddressMissing :
    MandateRefusal(HttpStatus.BAD_REQUEST, "MandateAddressMissing", "An online mandate names the member's address.")

/** The member agreed to a wording that is not the current one: the form they saw is out of date. */
class MandateWordingOutdated : MandateRefusal(HttpStatus.CONFLICT, "MandateWordingOutdated", "The authorisation wording has changed.")

/** A paper mandate would replace one the member authorised online, and the board has not confirmed that. */
class ReplacesOnlineMandate(
    authorisedAt: Instant,
) : MandateRefusal(
        HttpStatus.CONFLICT,
        "ReplacesOnlineMandate",
        "This replaces the online mandate the member authorised.",
        mapOf("authorisedAt" to authorisedAt.toString()),
    )
