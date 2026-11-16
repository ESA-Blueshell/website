package net.blueshell.api.vote.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.stereotype.Repository

@Repository
interface VoteTrackRepository : BaseRepository<VoteTrack, Long> {
    fun findBySourceAndExternalId(
        source: TrackSource,
        externalId: String,
    ): VoteTrack?

    @EntityGraph(attributePaths = ["game"])
    fun findAllByGameIdAndMergedIntoIsNullOrderByAddedAt(gameId: Long): List<VoteTrack>

    fun findAllByMergedIntoId(trackId: Long): List<VoteTrack>
}
