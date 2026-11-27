package net.blueshell.api.vote.domain

import net.blueshell.api.file.api.SitePictures
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.vote.persistence.CandidateTrack
import net.blueshell.api.vote.persistence.CandidateTrackRepository
import net.blueshell.api.vote.persistence.VoteCandidate
import net.blueshell.api.vote.persistence.VoteCandidateRepository
import net.blueshell.api.vote.persistence.VoteKind
import net.blueshell.api.vote.persistence.VoteRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
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
 * behalf. Every picture a vote is read with comes from here or from our own storage.
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
    /**
     * Games matching [query] for the [kind] vote of [year]; for a kind of the year's games, only
     * those first released in [year].
     */
    fun search(
        query: String,
        kind: VoteKind,
        year: Int,
    ): List<CatalogueGame> {
        votes.of(kind, year)
        val trimmed = query.trim()
        // Nothing typed opens the list on the best-known games; a letter or so means nothing yet.
        if (trimmed.isNotEmpty() && trimmed.length < MIN_GAME_QUERY) return emptyList()
        return catalogue.search(trimmed, GAME_SEARCH_LIMIT, kind.releaseYearFor(year))
    }

    /**
     * A search result's small cover, fetched from IGDB here so the visitor's browser never asks it,
     * while some vote is open: covers are only searched for then.
     */
    fun coverPreview(imageId: String): RemotePicture {
        if (!votes.existsVotingAt(clock.instant())) throw NoVoteOpen()
        return catalogue.coverUrl(imageId, CoverSize.SMALL)?.let(pictures::fetch) ?: throw CoverUnfetchable()
    }

    /**
     * The game with that IGDB id, stored with a copy of its cover the first time anybody picks it,
     * while the [kind] vote of [year] is open.
     */
    fun storeCandidate(
        igdbId: Long,
        kind: VoteKind,
        year: Int,
    ): CandidateSummary {
        votes.of(kind, year).requireVoting(clock.instant())
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
        userId: Long,
    ): TrackSummary {
        votes.of(kind, year).requireVoting(clock.instant())
        val link = TrackLink.parse(pasted)
        val candidate = storedCandidate(igdbId)
        existingTrack(link, candidate)?.let { return it }
        requireRoomForTrack(candidate)
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
                addedById = userId,
            )
        return try {
            transactions.execute { tracks.saveAndFlush(track).summary() }
        } catch (e: DataIntegrityViolationException) {
            // Somebody added the same link while its preview was fetched.
            existingTrack(link, candidate) ?: throw e
        }
    }

    /** Every live song whose title or game holds [term], game by game, for the board to tend. */
    @Transactional(readOnly = true)
    fun songs(term: String): List<TrackSummary> = tracks.findLiveMatching(term.trim()).map { it.summary() }

    /**
     * Gives the track a [title] of the member's own, for everybody who picked it: a video's name is
     * often more than the song's. Only the member who added the track may, or anybody the caller
     * says may rename any, which is the board.
     */
    @Transactional
    fun rename(
        trackId: Long,
        title: String,
        userId: Long,
        mayRenameAny: Boolean,
    ): TrackSummary {
        val track = tracks.findByIdOrNull(trackId) ?: throw TrackNotFound(trackId)
        if (!mayRenameAny && track.addedById != userId) throw TrackRenameNotAllowed()
        track.title = title.trim().take(TITLE_MAX)
        return tracks.saveAndFlush(track).summary()
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

    private fun requireRoomForTrack(candidate: VoteCandidate) {
        if (tracks.countByCandidateIdAndMergedIntoIsNull(candidate.id!!) >= TRACKS_PER_GAME) throw GameTrackLimit(TRACKS_PER_GAME)
    }

    // The link's track for [candidate], as the track it was merged into, where somebody already added it.
    private fun existingTrack(
        link: TrackLink,
        candidate: VoteCandidate,
    ): TrackSummary? =
        transactions.execute {
            tracks.findByCandidateIdAndSourceAndExternalId(candidate.id!!, link.source, link.externalId)?.canonical()?.summary()
        }

    // The stored game, copied from the catalogue with our copy of its cover the first time it is asked for.
    private fun storedCandidate(igdbId: Long): VoteCandidate {
        candidates.findByIgdbId(igdbId)?.let { return it }
        val found = catalogue.byId(igdbId) ?: throw VoteCandidateNotFound(igdbId)
        val cover =
            found.coverImageId
                ?.let { catalogue.coverUrl(it, CoverSize.BIG) }
                ?.let { copy(it, "igdb-${found.igdbId}.jpg", FileType.VOTE_COVER) }
        val candidate =
            VoteCandidate(
                igdbId = found.igdbId,
                name = found.name.take(TITLE_MAX),
                firstReleaseYear = found.firstReleaseYear,
                cover = cover,
            )
        return try {
            transactions.execute { candidates.saveAndFlush(candidate) }
        } catch (e: DataIntegrityViolationException) {
            // Somebody stored the same game while it was fetched.
            candidates.findByIgdbId(igdbId) ?: throw e
        }
    }

    // Our own copy of a picture, or none where it cannot be fetched or stored.
    private fun copy(
        url: String,
        name: String,
        kind: FileType,
    ) = pictures.fetch(url)?.let { stored.store(it.bytes, name, it.mediaType, kind) }

    private companion object {
        const val TITLE_MAX = 255

        // Each new track asks YouTube or Spotify about it, and every ballot form lists them all.
        const val TRACKS_PER_GAME = 30
    }
}
