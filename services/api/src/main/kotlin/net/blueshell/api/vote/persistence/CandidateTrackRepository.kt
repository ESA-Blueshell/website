package net.blueshell.api.vote.persistence

import jakarta.persistence.LockModeType
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
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

    /**
     * The track, with its version raised when the transaction commits even if nothing on it changed.
     * A merge reads both of its tracks this way, so A into B and B into C at once fail on B rather
     * than leave a chain the count does not follow.
     */
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("select t from CandidateTrack t where t.id = :id")
    fun findForMerge(id: Long): CandidateTrack?
}
