package net.blueshell.api.vote.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface VoteBallotRepository : BaseRepository<VoteBallot, Long> {
    fun findByVoteIdAndUserId(
        voteId: Long,
        userId: Long,
    ): VoteBallot?

    fun findAllByVoteId(voteId: Long): List<VoteBallot>

    fun countByVoteId(voteId: Long): Long

    /**
     * Deletes every ballot of the vote in one statement, a ballot committed after the count was read
     * included. Their picks go with them through the foreign key's cascade.
     */
    @Modifying
    @Query("delete from VoteBallot b where b.voteId = :voteId")
    fun deleteAllOfVote(voteId: Long): Int
}
