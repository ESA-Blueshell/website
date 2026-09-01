package net.blueshell.api.board.domain

import org.springframework.http.HttpStatus

// A code and the facts, never the sentence: `boards/refusals.ts` writes that. See ADR-026.
sealed class BoardRefusal(
    val status: HttpStatus,
    val code: String,
    val summary: String,
    val facts: Map<String, Any>,
) : RuntimeException(summary)

/**
 * A board's seats are the record of who sat that year, and [net.blueshell.api.board.persistence.Board]
 * cascades every write to them, so one delete takes the whole year's people with it.
 *
 * The count rides along so the answer says what stands in the way rather than only that
 * something does.
 */
class BoardHoldsSeats(number: Int, seats: Long) : BoardRefusal(
    HttpStatus.CONFLICT,
    "BoardHoldsSeats",
    "That board cannot be removed.",
    mapOf("number" to number, "seats" to seats),
)
