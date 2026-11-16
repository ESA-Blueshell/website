package net.blueshell.api.vote.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.stereotype.Repository

@Repository
interface VoteCandidateRepository : BaseRepository<VoteCandidate, Long> {
    // The cover comes with it, so the candidate can be drawn after the transaction that read it ends.
    @EntityGraph(attributePaths = ["cover"])
    fun findByIgdbId(igdbId: Long): VoteCandidate?
}
