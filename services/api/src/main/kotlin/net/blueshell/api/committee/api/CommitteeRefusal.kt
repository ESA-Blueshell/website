package net.blueshell.api.committee.api

import org.springframework.http.HttpStatus

// A code and the facts, never the sentence: the frontend writes that. See ADR-026.
sealed class CommitteeRefusal(
    val status: HttpStatus,
    val code: String,
    val summary: String,
    val facts: Map<String, Any>,
) : RuntimeException(summary)

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
