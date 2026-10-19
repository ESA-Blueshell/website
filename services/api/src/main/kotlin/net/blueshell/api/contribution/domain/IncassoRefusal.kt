package net.blueshell.api.contribution.domain

import net.blueshell.api.shared.refusal.Refusal
import org.springframework.http.HttpStatus
import java.time.LocalDate

// A code and the facts, never the sentence: `domains/contribution/refusals.ts` writes that. See ADR-026.
sealed class IncassoRefusal(
    status: HttpStatus,
    code: String,
    summary: String,
    facts: Map<String, Any> = emptyMap(),
) : Refusal(status, code, summary, facts)

class NothingToCollect : IncassoRefusal(HttpStatus.BAD_REQUEST, "NothingToCollect", "An incasso collects from somebody.")

class NotCollectable(
    userIds: List<Long>,
) : IncassoRefusal(
        HttpStatus.CONFLICT,
        "NotCollectable",
        "Somebody chosen cannot be collected from.",
        mapOf("userIds" to userIds),
    )

/** The notifications go out as the run starts, at least 14 days before the collection (api ADR-040). */
class CollectionDateTooSoon(
    earliest: LocalDate,
) : IncassoRefusal(
        HttpStatus.BAD_REQUEST,
        "CollectionDateTooSoon",
        "A collection date is at least 14 days after the notifications go out.",
        mapOf("earliest" to earliest.toString()),
    )

class CollectionDateOutsidePeriod :
    IncassoRefusal(
        HttpStatus.BAD_REQUEST,
        "CollectionDateOutsidePeriod",
        "The collection date falls outside the contribution period.",
    )

class StatementTextMissing : IncassoRefusal(HttpStatus.BAD_REQUEST, "StatementTextMissing", "The bank statement says what is collected.")

class StatementTextTooLong(
    max: Int,
) : IncassoRefusal(HttpStatus.BAD_REQUEST, "StatementTextTooLong", "The statement text is too long.", mapOf("max" to max))

class IncassoRunNotFound : IncassoRefusal(HttpStatus.NOT_FOUND, "IncassoRunNotFound", "There is no such incasso run.")

class IncassoRunSubmitted : IncassoRefusal(HttpStatus.CONFLICT, "IncassoRunSubmitted", "The incasso is already in ING.")

class CollectionDatePassed : IncassoRefusal(HttpStatus.CONFLICT, "CollectionDatePassed", "The collection date has passed.")

class IngDetailsMissing :
    IncassoRefusal(
        HttpStatus.CONFLICT,
        "IngDetailsMissing",
        "The association's IBAN or incassant ID is not configured.",
    )

class MandateChanged(
    userIds: List<Long>,
) : IncassoRefusal(
        HttpStatus.CONFLICT,
        "MandateChanged",
        "A member's mandate changed after they were told.",
        mapOf("userIds" to userIds),
    )

class StornoBeforeSubmitted : IncassoRefusal(HttpStatus.CONFLICT, "StornoBeforeSubmitted", "A storno follows a run sent to the bank.")

class NotInRun : IncassoRefusal(HttpStatus.NOT_FOUND, "NotInRun", "That member is not collected from in this run.")

class IncassoFilePartNotFound : IncassoRefusal(HttpStatus.NOT_FOUND, "IncassoFilePartNotFound", "The incasso has no such file.")
