package net.blueshell.api.vote.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface VoteBallotRepository : BaseRepository<VoteBallot, Long> {
    fun findByVoteIdAndUserId(
        voteId: Long,
        userId: Long,
    ): VoteBallot?

    fun findAllByVoteId(voteId: Long): List<VoteBallot>

    fun countByVoteId(voteId: Long): Long
}
