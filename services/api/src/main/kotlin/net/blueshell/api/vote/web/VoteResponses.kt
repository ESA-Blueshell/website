package net.blueshell.api.vote.web

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.vote.domain.BallotSummary
import net.blueshell.api.vote.domain.CatalogueGame
import net.blueshell.api.vote.domain.GameSummary
import net.blueshell.api.vote.domain.PickSummary
import net.blueshell.api.vote.domain.Placing
import net.blueshell.api.vote.domain.TrackSummary
import net.blueshell.api.vote.domain.VoteState
import net.blueshell.api.vote.domain.VoteSummary
import net.blueshell.api.vote.persistence.TrackSource
import net.blueshell.api.vote.persistence.VoteKind
import java.time.Instant

/** A search result. Its cover is [coverImageId], fetched through `/vote-games/covers`. */
@Schema(name = "CatalogueGameResponse")
data class CatalogueGameResponse(
    val igdbId: Long,
    val name: String,
    val firstReleaseYear: Int?,
    val coverImageId: String?,
)

/** A game somebody picked. [coverPath] is our stored copy, resolved against the api. */
@Schema(name = "VoteGameResponse")
data class VoteGameResponse(
    val id: Long,
    val igdbId: Long,
    val name: String,
    val firstReleaseYear: Int?,
    val coverPath: String?,
)

@Schema(name = "VoteTrackResponse")
data class VoteTrackResponse(
    val id: Long,
    val game: VoteGameResponse,
    val source: TrackSource,
    val externalId: String,
    val url: String,
    val title: String,
    val thumbnailPath: String?,
)

@Schema(name = "VotePickResponse")
data class VotePickResponse(
    val game: VoteGameResponse,
    val track: VoteTrackResponse?,
)

@Schema(name = "VotePlacingResponse")
data class VotePlacingResponse(
    val place: Int,
    val game: VoteGameResponse,
    val track: VoteTrackResponse?,
    val points: Int,
    val voters: Int,
)

/**
 * A year's vote, with what its kind asks for: [points] per position, so its size is how many picks
 * a ballot holds. [results] is empty until it is published.
 */
@Schema(name = "AnnualVoteResponse")
data class AnnualVoteResponse(
    val kind: VoteKind,
    val year: Int,
    val state: VoteState,
    val opensAt: Instant,
    val closesAt: Instant,
    val revealAt: Instant,
    val ballots: Int,
    val results: List<VotePlacingResponse>,
    val points: List<Int>,
    val picksTrack: Boolean,
    val releasedInYear: Boolean,
)

/** The member's own ballot; [version] is null and [picks] empty until they have voted. */
@Schema(name = "VoteBallotResponse")
data class VoteBallotResponse(
    val version: Long?,
    val submittedAt: Instant?,
    val picks: List<VotePickResponse>,
)

internal fun CatalogueGame.asResponse() = CatalogueGameResponse(igdbId, name, firstReleaseYear, coverImageId)

internal fun GameSummary.asResponse() = VoteGameResponse(id, igdbId, name, firstReleaseYear, coverPath)

internal fun TrackSummary.asResponse() = VoteTrackResponse(id, game.asResponse(), source, externalId, url, title, thumbnailPath)

internal fun PickSummary.asResponse() = VotePickResponse(game.asResponse(), track?.asResponse())

internal fun Placing.asResponse() = VotePlacingResponse(place, game.asResponse(), track?.asResponse(), points, voters)

internal fun VoteSummary.asResponse() =
    AnnualVoteResponse(
        kind = kind,
        year = year,
        state = state,
        opensAt = opensAt,
        closesAt = closesAt,
        revealAt = revealAt,
        ballots = ballots,
        results = results.map { it.asResponse() },
        points = kind.points,
        picksTrack = kind.picksTrack,
        releasedInYear = kind.releasedInYear,
    )

internal fun BallotSummary?.asResponse() =
    VoteBallotResponse(
        version = this?.version,
        submittedAt = this?.submittedAt,
        picks = this?.picks.orEmpty().map { it.asResponse() },
    )
