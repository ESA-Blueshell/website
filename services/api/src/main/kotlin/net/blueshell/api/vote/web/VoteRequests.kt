package net.blueshell.api.vote.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import net.blueshell.api.vote.domain.PickChoice
import net.blueshell.api.vote.persistence.VoteKind
import java.time.Instant

private const val FIRST_YEAR = 2000L
private const val LAST_YEAR = 2100L
private const val LINK_MAX = 512

// The most picks any kind's ballot holds; each kind's own limit is the vote's to enforce.

@Schema(name = "CreateAnnualVoteRequest")
data class CreateAnnualVoteRequest(
    @field:Min(FIRST_YEAR) @field:Max(LAST_YEAR)
    val year: Int,
    val opensAt: Instant,
    val closesAt: Instant,
    val revealAt: Instant,
)

@Schema(name = "RescheduleAnnualVoteRequest")
data class RescheduleAnnualVoteRequest(
    val opensAt: Instant,
    val closesAt: Instant,
    val revealAt: Instant,
    /** The vote's version as the form read it. */
    val version: Long,
)

/** A pick by our game id, with one of its tracks where the vote's kind picks one. */
@Schema(name = "VotePickRequest")
data class VotePickRequest(
    @field:Positive
    val candidateId: Long,
    @field:Positive
    val trackId: Long? = null,
) {
    fun choice() = PickChoice(candidateId, trackId)
}

/** The picks best first; [version] is the ballot as last read, null for a first ballot. */
@Schema(name = "CastVoteBallotRequest")
data class CastVoteBallotRequest(
    @field:Valid @field:Size(min = 1)
    val picks: List<VotePickRequest>,
    val version: Long? = null,
)

@Schema(name = "StoreVoteCandidateRequest")
data class StoreVoteCandidateRequest(
    @field:Positive
    val igdbId: Long,
    /** The vote the game is picked for, which must be open. */
    val kind: VoteKind,
    val year: Int,
)

@Schema(name = "AddVoteTrackRequest")
data class AddVoteTrackRequest(
    @field:Positive
    val igdbId: Long,
    @field:NotBlank @field:Size(max = LINK_MAX)
    val url: String,
    /** The vote the track is picked for, which must be open. */
    val kind: VoteKind,
    val year: Int,
)

@Schema(name = "MergeVoteTrackRequest")
data class MergeVoteTrackRequest(
    @field:Positive
    val intoId: Long,
)
