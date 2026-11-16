package net.blueshell.api.vote.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.stereotype.Repository

@Repository
interface VotePlacingRepository : BaseRepository<VotePlacing, Long> {
    @EntityGraph(attributePaths = ["candidate", "track"])
    fun findAllByVoteIdOrderByPlaceAscCandidateNameAsc(voteId: Long): List<VotePlacing>
}
