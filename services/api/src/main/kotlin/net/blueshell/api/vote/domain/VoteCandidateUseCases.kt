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
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/** The shortest query worth sending to the catalogue. */
const val MIN_GAME_QUERY = 2

/** How many games a search answers with. */
const val GAME_SEARCH_LIMIT = 10

/**
 * Finding games and the tracks already picked for one, storing a game or a track the first time
 * somebody picks it, merging duplicate tracks, and fetching a search result's cover on a visitor's
 * behalf. Every picture a page shows comes from here or from our own storage.
 */
@Service
class VoteCandidateUseCases(
    private val catalogue: GameCatalogue,
    private val previews: TrackPreviews,
    private val pictures: RemotePictures,
    private val stored: SitePictures,
    private val candidates: VoteCandidateRepository,
    private val tracks: CandidateTrackRepository,
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

    /** The game with that IGDB id, stored with a copy of its cover the first time anybody picks it. */
    @Transactional
    fun candidate(igdbId: Long): CandidateSummary = (candidates.findByIgdbId(igdbId) ?: candidateFromCatalogue(igdbId)).summary()

    /** The tracks already picked for a game, first added first; none for a game nobody picked. */
    @Transactional(readOnly = true)
    fun tracksOf(igdbId: Long): List<TrackSummary> {
        val candidate = candidates.findByIgdbId(igdbId) ?: return emptyList()
        return tracks.findAllByCandidateIdAndMergedIntoIsNullOrderByAddedAt(candidate.id!!).map { it.summary() }
    }

    /**
     * The track [pasted] links to, as a track of the game [igdbId]. A link somebody already added
     * answers with that track, so a ballot never holds two rows for one link.
     */
    @Transactional
    fun addTrack(
        igdbId: Long,
        pasted: String,
    ): TrackSummary {
        val link = TrackLink.parse(pasted)
        tracks.findBySourceAndExternalId(link.source, link.externalId)?.canonical()?.let { existing ->
            if (existing.candidate.igdbId != igdbId) throw TrackOfAnotherGame(existing.id!!, existing.candidate.name)
            return existing.summary()
        }
        val candidate = candidates.findByIgdbId(igdbId) ?: candidateFromCatalogue(igdbId)
        val preview = previews.of(link) ?: throw TrackLinkUnreadable()
        val track =
            CandidateTrack(
                candidate = candidate,
                source = link.source,
                externalId = link.externalId,
                title = preview.title.take(TITLE_MAX),
                addedAt = clock.instant(),
                thumbnail =
                    preview.thumbnailUrl?.let {
                        copy(
                            it,
                            "${link.source.name.lowercase()}-${link.externalId}.jpg",
                            FileType.VOTE_THUMBNAIL,
                        )
                    },
            )
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
        val track = tracks.findById(trackId).orElseThrow { TrackNotFound(trackId) }
        val into = tracks.findById(intoId).orElseThrow { TrackNotFound(intoId) }
        val valid = trackId != intoId && track.mergedInto == null && into.mergedInto == null && track.candidate.id == into.candidate.id
        if (!valid) throw TrackMergeInvalid(trackId, intoId)
        track.mergedInto = into
        tracks.findAllByMergedIntoId(trackId).forEach { it.mergedInto = into }
        tracks.flush()
        return into.summary()
    }

    private fun candidateFromCatalogue(igdbId: Long): VoteCandidate {
        val found = catalogue.byId(igdbId) ?: throw VoteCandidateNotFound(igdbId)
        return candidates.saveAndFlush(
            VoteCandidate(
                igdbId = found.igdbId,
                name = found.name.take(TITLE_MAX),
                firstReleaseYear = found.firstReleaseYear,
                cover =
                    found.coverImageId
                        ?.let { igdbCoverUrl(it, "t_cover_big") }
                        ?.let { copy(it, "igdb-${found.igdbId}.jpg", FileType.VOTE_COVER) },
            ),
        )
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
