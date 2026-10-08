package net.blueshell.api.committee.api

import net.blueshell.api.shared.refusal.Refusal
import org.springframework.http.HttpStatus

// A code and the facts, never the sentence: `committees/refusals.ts` writes that. See ADR-026.
sealed class CommitteeRefusal(
    status: HttpStatus,
    code: String,
    summary: String,
    facts: Map<String, Any>,
) : Refusal(status, code, summary, facts)

class CommitteeAddressBlank :
    CommitteeRefusal(HttpStatus.BAD_REQUEST, "CommitteeAddressBlank", "A committee needs an address.", emptyMap())

class CommitteeAddressTaken(
    committeeName: String,
    address: String,
) : CommitteeRefusal(
        HttpStatus.CONFLICT,
        "CommitteeAddressTaken",
        "That address is already used by another committee.",
        mapOf("committeeName" to committeeName, "address" to address),
    )

class UnknownCommitteeAddress(
    address: String,
) : CommitteeRefusal(HttpStatus.NOT_FOUND, "UnknownCommitteeAddress", "No committee has that address.", mapOf("address" to address))

class CommitteeEventsNeedTaker(
    events: Long,
) : CommitteeRefusal(
        HttpStatus.CONFLICT,
        "CommitteeEventsNeedTaker",
        "A committee with events is deleted only once another committee takes them over.",
        mapOf("events" to events),
    )

class CommitteeCannotTakeOwnEvents :
    CommitteeRefusal(HttpStatus.BAD_REQUEST, "CommitteeCannotTakeOwnEvents", "A committee cannot take over its own events.", emptyMap())

class ArchivedCommitteeCannotTakeEvents(
    committeeName: String,
) : CommitteeRefusal(
        HttpStatus.BAD_REQUEST,
        "ArchivedCommitteeCannotTakeEvents",
        "An archived committee cannot take over events.",
        mapOf("committeeName" to committeeName),
    )

class CommitteeNotFound(
    id: Long,
) : CommitteeRefusal(HttpStatus.NOT_FOUND, "CommitteeNotFound", "That committee does not exist.", mapOf("id" to id))
