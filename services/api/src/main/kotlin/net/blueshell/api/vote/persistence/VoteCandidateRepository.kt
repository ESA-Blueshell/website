package net.blueshell.api.vote.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface VoteCandidateRepository : BaseRepository<VoteCandidate, Long> {
    fun findByIgdbId(igdbId: Long): VoteCandidate?
}
