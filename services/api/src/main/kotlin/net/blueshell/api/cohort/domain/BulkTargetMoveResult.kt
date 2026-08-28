package net.blueshell.api.cohort.domain

import io.swagger.v3.oas.annotations.media.Schema

/**
 * What became of a bulk move.
 *
 * The whole selection is checked before anything is sent, so a request that would not work is
 * refused with nothing written. Past that point the moves are separate calls to a system that
 * has no transaction to roll back — the fifth can fail with four already filed elsewhere. That
 * really is a partial outcome, and saying so is more use than a failure that hides four moves
 * that happened or a success that hides one that did not.
 */
@Schema(name = "BulkTargetMoveResult", description = "The outcome of moving several targets.")
data class BulkTargetMoveResult(
    @field:Schema(description = "The targets that were moved, as the system now describes them.")
    val moved: List<ExternalTarget>,

    @field:Schema(description = "The targets the system refused, and what it said. Empty when all moved.")
    val failed: List<FailedTargetMove>,
)

@Schema(name = "FailedTargetMove", description = "One target the external system would not move.")
data class FailedTargetMove(
    val externalId: String,
    val label: String,
    @field:Schema(description = "What the system said, for an operator to act on rather than a stack trace.")
    val message: String,
)
