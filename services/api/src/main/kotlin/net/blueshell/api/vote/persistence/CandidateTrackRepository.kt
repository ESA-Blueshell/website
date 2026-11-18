package net.blueshell.api.vote.persistence

import jakarta.persistence.LockModeType
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface CandidateTrackRepository : BaseRepository<CandidateTrack, Long> {
    fun findByCandidateIdAndSourceAndExternalId(
        candidateId: Long,
        source: TrackSource,
        externalId: String,
    ): CandidateTrack?

    // A track is read with its game, the game's cover and its own thumbnail; renditions come in batches.
    @EntityGraph(attributePaths = ["candidate", "candidate.cover", "thumbnail"])
    fun findAllByCandidateIdAndMergedIntoIsNullOrderByAddedAt(candidateId: Long): List<CandidateTrack>

    fun findAllByMergedIntoId(trackId: Long): List<CandidateTrack>

    /**
     * Every live track whose title or game's name holds [term], game by game; the board's list of
     * songs. An empty [term] holds for every track.
     */
    @EntityGraph(attributePaths = ["candidate", "candidate.cover", "thumbnail"])
    @Query(
        "select t from CandidateTrack t where t.mergedInto is null and " +
            "(lower(t.title) like lower(concat('%', :term, '%')) or lower(t.candidate.name) like lower(concat('%', :term, '%'))) " +
            "order by t.candidate.name, t.addedAt",
    )
    fun findLiveMatching(term: String): List<CandidateTrack>

    @EntityGraph(attributePaths = ["candidate", "candidate.cover", "thumbnail"])
    fun findAllByIdIn(ids: Collection<Long>): List<CandidateTrack>

    /** How many live tracks a game has, merged ones left out. */
    fun countByCandidateIdAndMergedIntoIsNull(candidateId: Long): Long

    /**
     * The track, with its version raised when the transaction commits even if nothing on it changed.
     * A merge reads both of its tracks this way, so A into B and B into C at once fail on B rather
     * than leave a chain the count does not follow.
     */
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("select t from CandidateTrack t where t.id = :id")
    fun findForMerge(id: Long): CandidateTrack?
}
