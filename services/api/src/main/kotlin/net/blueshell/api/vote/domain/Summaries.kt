package net.blueshell.api.vote.domain

import net.blueshell.api.file.api.PublicFileUrls
import net.blueshell.api.vote.persistence.AnnualVote
import net.blueshell.api.vote.persistence.TrackSource
import net.blueshell.api.vote.persistence.VoteGame
import net.blueshell.api.vote.persistence.VoteKind
import net.blueshell.api.vote.persistence.VoteTrack
import java.time.Instant

/** A game as a page draws it. [coverPath] is our stored copy's address, never IGDB's. */
data class GameSummary(
    val id: Long,
    val igdbId: Long,
    val name: String,
    val firstReleaseYear: Int?,
    val coverPath: String?,
)

/** A track as a page draws it. [thumbnailPath] is our stored copy's address, never theirs. */
data class TrackSummary(
    val id: Long,
    val game: GameSummary,
    val source: TrackSource,
    val externalId: String,
    val url: String,
    val title: String,
    val thumbnailPath: String?,
)

/** One pick on a ballot: the game, and its track where the kind picks one. */
data class PickSummary(
    val game: GameSummary,
    val track: TrackSummary?,
)

/** A game's place, the track that represents it where the kind picks tracks, and what put it there. */
data class Placing(
    val place: Int,
    val game: GameSummary,
    val track: TrackSummary?,
    val points: Int,
    val voters: Int,
)

data class BallotSummary(
    val version: Long,
    val submittedAt: Instant,
    val picks: List<PickSummary>,
)

/**
 * A year's vote as anybody may see it: [results] only once it is published, and [ballots] counted
 * live until then.
 */
data class VoteSummary(
    val kind: VoteKind,
    val year: Int,
    val state: VoteState,
    val opensAt: Instant,
    val closesAt: Instant,
    val revealAt: Instant,
    val ballots: Int,
    val results: List<Placing>,
)

/** What a pick asks for: a game by our id, and one of its tracks where the kind picks one. */
data class PickChoice(
    val gameId: Long,
    val trackId: Long? = null,
)

internal fun VoteGame.summary() = GameSummary(id!!, igdbId, name, firstReleaseYear, cover?.let(PublicFileUrls::of))

internal fun VoteTrack.summary() =
    TrackSummary(
        id = id!!,
        game = game.summary(),
        source = source,
        externalId = externalId,
        url = TrackLink(source, externalId).url,
        title = title,
        thumbnailPath = thumbnail?.let(PublicFileUrls::of),
    )

/** The track a merged duplicate counts as; merging keeps one hop, so this never chains. */
internal fun VoteTrack.canonical(): VoteTrack = mergedInto ?: this

/** Opens, then closes, then the reveal no earlier than the close. */
internal fun requireInOrder(
    opensAt: Instant,
    closesAt: Instant,
    revealAt: Instant,
) {
    if (!opensAt.isBefore(closesAt) || revealAt.isBefore(closesAt)) throw VoteDatesOutOfOrder()
}

internal fun AnnualVote.summaryWith(
    state: VoteState,
    ballots: Int,
    results: List<Placing>,
) = VoteSummary(kind, year, state, opensAt, closesAt, revealAt, ballots, results)
