package net.blueshell.api.vote.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.stereotype.Repository

@Repository
interface VoteResultRepository : BaseRepository<VoteResult, Long> {
    @EntityGraph(attributePaths = ["game", "track"])
    fun findAllByVoteIdOrderByPlaceAscGameNameAsc(voteId: Long): List<VoteResult>
}
