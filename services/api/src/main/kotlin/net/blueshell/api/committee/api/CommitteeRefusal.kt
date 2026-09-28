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

class CommitteeNotFound(
    id: Long,
) : CommitteeRefusal(HttpStatus.NOT_FOUND, "CommitteeNotFound", "That committee does not exist.", mapOf("id" to id))
