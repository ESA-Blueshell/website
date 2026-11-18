package net.blueshell.api.vote.web

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.file.api.Image
import net.blueshell.api.vote.domain.VoteState
import net.blueshell.api.vote.persistence.TrackSource
import net.blueshell.api.vote.persistence.VoteKind
import java.time.Instant

/** A search result. Its cover is [coverImageId], fetched through `/vote-candidates/covers/{imageId}`. */
@Schema(name = "CatalogueGameResponse")
data class CatalogueGameResponse(
    val igdbId: Long,
    val name: String,
    val firstReleaseYear: Int?,
    val coverImageId: String?,
)

/** A game somebody picked. [cover] is our stored copy, with the widths it is stored at. */
@Schema(name = "VoteCandidateResponse")
data class VoteCandidateResponse(
    val id: Long,
    val igdbId: Long,
    val name: String,
    val firstReleaseYear: Int?,
    val cover: Image?,
)

@Schema(name = "CandidateTrackResponse")
data class CandidateTrackResponse(
    val id: Long,
    val candidate: VoteCandidateResponse,
    val source: TrackSource,
    val externalId: String,
    val url: String,
    /** Where the track plays inside a page. */
    val embedUrl: String,
    val title: String,
    val thumbnail: Image?,
)

@Schema(name = "VotePickResponse")
data class VotePickResponse(
    val candidate: VoteCandidateResponse,
    val track: CandidateTrackResponse?,
)

@Schema(name = "VotePlacingResponse")
data class VotePlacingResponse(
    val place: Int,
    val candidate: VoteCandidateResponse,
    val track: CandidateTrackResponse?,
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
    val version: Long,
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
