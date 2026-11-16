package net.blueshell.api.vote.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.stereotype.Repository

@Repository
interface CandidateTrackRepository : BaseRepository<CandidateTrack, Long> {
    fun findBySourceAndExternalId(
        source: TrackSource,
        externalId: String,
    ): CandidateTrack?

    @EntityGraph(attributePaths = ["candidate"])
    fun findAllByCandidateIdAndMergedIntoIsNullOrderByAddedAt(candidateId: Long): List<CandidateTrack>

    fun findAllByMergedIntoId(trackId: Long): List<CandidateTrack>
}
