package net.blueshell.api.vote.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.stereotype.Repository

@Repository
interface VotePlacingRepository : BaseRepository<VotePlacing, Long> {
    // Each placing is read with its cover and thumbnail, so they come with it.
    @EntityGraph(attributePaths = ["candidate", "candidate.cover", "track", "track.thumbnail"])
    fun findAllByVoteIdOrderByPlaceAscCandidateNameAsc(voteId: Long): List<VotePlacing>
}
