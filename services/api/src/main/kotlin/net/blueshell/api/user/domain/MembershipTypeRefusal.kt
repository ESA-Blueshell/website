package net.blueshell.api.user.domain

import net.blueshell.api.shared.refusal.Refusal
import org.springframework.http.HttpStatus
import java.time.LocalDate

// A code and the facts, never the sentence: `domains/user/refusals.ts` writes that. See ADR-026.
sealed class MembershipTypeRefusal(
    code: String,
    summary: String,
    facts: Map<String, Any> = emptyMap(),
    status: HttpStatus = HttpStatus.BAD_REQUEST,
) : Refusal(status, code, summary, facts)

/** A type starts on a day the membership runs: from its start, and before its end. */
class MembershipTypeOutsideMembership(
    startDate: LocalDate,
    endDate: LocalDate?,
) : MembershipTypeRefusal(
        "MembershipTypeOutsideMembership",
        "A membership type starts while the membership runs.",
        listOfNotNull("startDate" to startDate.toString(), endDate?.let { "endDate" to it.toString() }).toMap(),
    )

class MembershipTypeOnTakenDay(
    since: LocalDate,
) : MembershipTypeRefusal(
        "MembershipTypeOnTakenDay",
        "Another membership type starts on that day.",
        mapOf("since" to since.toString()),
    )

/** The first type is what the membership started as, so it is retyped in place but never moved or removed. */
class FirstMembershipTypeStartsTheMembership :
    MembershipTypeRefusal(
        "FirstMembershipTypeStartsTheMembership",
        "The first membership type starts with the membership.",
    )

class MembershipTypeNotFound(
    id: Long,
) : MembershipTypeRefusal("MembershipTypeNotFound", "No such membership type.", mapOf("id" to id), HttpStatus.NOT_FOUND)
