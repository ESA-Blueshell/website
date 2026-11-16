package net.blueshell.api.vote.domain

import net.blueshell.api.file.api.RemotePicture
import net.blueshell.api.file.api.RemotePictures
import net.blueshell.api.file.api.SitePictures
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.vote.persistence.CandidateTrack
import net.blueshell.api.vote.persistence.CandidateTrackRepository
import net.blueshell.api.vote.persistence.VoteCandidate
import net.blueshell.api.vote.persistence.VoteCandidateRepository
import net.blueshell.api.vote.persistence.VoteKind
import net.blueshell.api.vote.persistence.VoteRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock

/** The shortest query worth sending to the catalogue. */
const val MIN_GAME_QUERY = 2

/** How many games a search answers with. */
const val GAME_SEARCH_LIMIT = 10

/**
 * Finding games and the tracks already picked for one, storing a game or a track the first time
 * somebody picks it, merging duplicate tracks, and fetching a search result's cover on a visitor's
 * behalf. Every picture a page shows comes from here or from our own storage.
 *
 * Storing asks IGDB, YouTube or Spotify and fetches a picture before it writes, each call taking up
 * to seconds, so those calls run outside any transaction and only the reads and the write hold a
 * connection.
 */
@Service
class VoteCandidateUseCases(
    private val catalogue: GameCatalogue,
    private val previews: TrackPreviews,
    private val pictures: RemotePictures,
    private val stored: SitePictures,
    private val votes: VoteRepository,
    private val candidates: VoteCandidateRepository,
    private val tracks: CandidateTrackRepository,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) {
    /** Games matching [query]; for a kind of the year's games, only those first released in [year]. */
    fun search(
        query: String,
        kind: VoteKind,
        year: Int,
    ): List<CatalogueGame> {
        val trimmed = query.trim()
        if (trimmed.length < MIN_GAME_QUERY) return emptyList()
        return catalogue.search(trimmed, GAME_SEARCH_LIMIT, year.takeIf { kind.releasedInYear })
    }

    /** A search result's small cover, fetched from IGDB here so the visitor's browser never asks it. */
    fun coverPreview(imageId: String): RemotePicture = igdbCoverUrl(imageId, "t_cover_small")?.let(pictures::fetch) ?: throw CoverNotFound()

    /**
     * The game with that IGDB id, stored with a copy of its cover the first time anybody picks it,
     * while the [kind] vote of [year] is open.
     */
    fun candidate(
        igdbId: Long,
        kind: VoteKind,
        year: Int,
    ): CandidateSummary {
        requireVoting(kind, year)
        return storedCandidate(igdbId).summary()
    }

    /** The tracks already picked for a game, first added first; none for a game nobody picked. */
    @Transactional(readOnly = true)
    fun tracksOf(igdbId: Long): List<TrackSummary> {
        val candidate = candidates.findByIgdbId(igdbId) ?: return emptyList()
        return tracks.findAllByCandidateIdAndMergedIntoIsNullOrderByAddedAt(candidate.id!!).map { it.summary() }
    }

    /**
     * The track [pasted] links to, as a track of the game [igdbId], while the [kind] vote of [year]
     * is open. A link somebody already added for that game answers with that track, so a ballot
     * never holds two rows for one link.
     */
    fun addTrack(
        igdbId: Long,
        pasted: String,
        kind: VoteKind,
        year: Int,
    ): TrackSummary {
        requireVoting(kind, year)
        val link = TrackLink.parse(pasted)
        val candidate = storedCandidate(igdbId)
        existingTrack(link, candidate)?.let { return it }
        val preview = previews.of(link) ?: throw TrackLinkUnreadable()
        val thumbnailName = "${link.source.name.lowercase()}-${link.externalId}.jpg"
        val thumbnail = preview.thumbnailUrl?.let { copy(it, thumbnailName, FileType.VOTE_THUMBNAIL) }
        val track =
            CandidateTrack(
                candidate = candidate,
                source = link.source,
                externalId = link.externalId,
                title = preview.title.take(TITLE_MAX),
                addedAt = clock.instant(),
                thumbnail = thumbnail,
            )
        return try {
            written { tracks.saveAndFlush(track).summary() }
        } catch (e: DataIntegrityViolationException) {
            // Somebody added the same link while its preview was fetched.
            existingTrack(link, candidate) ?: throw e
        }
    }

    /**
     * Counts [trackId] as [intoId] from now on, along with anything already merged into it, so a
     * merge never chains. Both must be live tracks of one game.
     */
    @Transactional
    fun merge(
        trackId: Long,
        intoId: Long,
    ): TrackSummary {
        val track = tracks.findForMerge(trackId) ?: throw TrackNotFound(trackId)
        val into = tracks.findForMerge(intoId) ?: throw TrackNotFound(intoId)
        val valid = trackId != intoId && track.mergedInto == null && into.mergedInto == null && track.candidate.id == into.candidate.id
        if (!valid) throw TrackMergeInvalid(trackId, intoId)
        track.mergedInto = into
        tracks.findAllByMergedIntoId(trackId).forEach { it.mergedInto = into }
        tracks.flush()
        return into.summary()
    }

    // Members store games and tracks, with their pictures, only while a vote they are picked for is open.
    private fun requireVoting(
        kind: VoteKind,
        year: Int,
    ) {
        val vote = votes.findByKindAndYear(kind, year) ?: throw AnnualVoteNotFound(kind, year)
        val state = vote.stateAt(clock.instant())
        if (state != VoteState.VOTING) throw WrongVoteState(state)
    }

    // The link's track for [candidate], as the track it was merged into, where somebody already added it.
    private fun existingTrack(
        link: TrackLink,
        candidate: VoteCandidate,
    ): TrackSummary? =
        written {
            tracks.findByCandidateIdAndSourceAndExternalId(candidate.id!!, link.source, link.externalId)?.canonical()?.summary()
        }

    // The stored game, copied from the catalogue with our copy of its cover the first time it is asked for.
    private fun storedCandidate(igdbId: Long): VoteCandidate {
        candidates.findByIgdbId(igdbId)?.let { return it }
        val found = catalogue.byId(igdbId) ?: throw VoteCandidateNotFound(igdbId)
        val cover =
            found.coverImageId
                ?.let { igdbCoverUrl(it, "t_cover_big") }
                ?.let { copy(it, "igdb-${found.igdbId}.jpg", FileType.VOTE_COVER) }
        val candidate =
            VoteCandidate(
                igdbId = found.igdbId,
                name = found.name.take(TITLE_MAX),
                firstReleaseYear = found.firstReleaseYear,
                cover = cover,
            )
        return try {
            written { candidates.saveAndFlush(candidate) }
        } catch (e: DataIntegrityViolationException) {
            // Somebody stored the same game while it was fetched.
            candidates.findByIgdbId(igdbId) ?: throw e
        }
    }

    // [work] in a transaction of its own; what it throws rolls the transaction back and reaches the caller.
    private fun <T> written(work: () -> T): T {
        val answer = mutableListOf<T>()
        transactions.executeWithoutResult { answer += work() }
        return answer.single()
    }

    // Our own copy of a picture, or none where it cannot be fetched or stored.
    private fun copy(
        url: String,
        name: String,
        kind: FileType,
    ) = pictures.fetch(url)?.let { stored.store(it.bytes, name, it.mediaType, kind) }

    private companion object {
        const val TITLE_MAX = 255
    }
}
