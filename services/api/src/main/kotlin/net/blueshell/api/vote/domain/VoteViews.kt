package net.blueshell.api.vote.domain

import net.blueshell.api.file.api.Image
import net.blueshell.api.file.api.asImage
import net.blueshell.api.vote.persistence.CandidateTrack
import net.blueshell.api.vote.persistence.TrackSource
import net.blueshell.api.vote.persistence.Vote
import net.blueshell.api.vote.persistence.VoteCandidate
import net.blueshell.api.vote.persistence.VoteKind
import java.time.Instant

/** A game as a vote reads it. [cover] is our stored copy, never IGDB's. */
data class CandidateSummary(
    val id: Long,
    val igdbId: Long,
    val name: String,
    val firstReleaseYear: Int?,
    val cover: Image?,
)

/** A track as a vote reads it. [thumbnail] is our stored copy, never theirs. */
data class TrackSummary(
    val id: Long,
    val candidate: CandidateSummary,
    val source: TrackSource,
    val externalId: String,
    val url: String,
    val embedUrl: String,
    val title: String,
    val thumbnail: Image?,
)

/** One pick on a ballot: the game, and its track where the kind picks one. */
data class PickSummary(
    val candidate: CandidateSummary,
    val track: TrackSummary?,
)

/** A game's place, the track that represents it where the kind picks tracks, and what put it there. */
data class Placing(
    val place: Int,
    val candidate: CandidateSummary,
    val track: TrackSummary?,
    val points: Int,
    val voters: Int,
)

data class BallotSummary(
    val version: Long,
    val submittedAt: Instant,
    val picks: List<PickSummary>,
    /** The tracks on the ballot its member added, and so may rename. */
    val renamableTrackIds: List<Long>,
)

/**
 * A year's vote as anybody may see it: [results] only once it is revealed, and [ballots] counted
 * live until then.
 */
data class VoteSummary(
    val kind: VoteKind,
    val year: Int,
    val version: Long,
    val state: VoteState,
    val opensAt: Instant,
    val closesAt: Instant,
    val revealAt: Instant,
    val ballots: Int,
    val results: List<Placing>,
    /** What each place on a ballot is worth, best first; its size is how many picks a ballot holds. */
    val points: List<Int>,
)

/** What a pick asks for: a game by our id, and one of its tracks where the kind picks one. */
data class PickChoice(
    val candidateId: Long,
    val trackId: Long? = null,
)

internal fun VoteCandidate.summary() = CandidateSummary(id!!, igdbId, name, firstReleaseYear, cover?.asImage())

internal fun CandidateTrack.summary() =
    TrackSummary(
        id = id!!,
        candidate = candidate.summary(),
        source = source,
        externalId = externalId,
        url = source.service.watchUrl(externalId),
        embedUrl = source.service.embedUrl(externalId),
        title = title,
        thumbnail = thumbnail?.asImage(),
    )

internal fun Vote.summaryWith(
    state: VoteState,
    ballots: Int,
    results: List<Placing>,
) = VoteSummary(kind, year, version, state, opensAt, closesAt, revealAt, ballots, results, pointsFor(picks))
