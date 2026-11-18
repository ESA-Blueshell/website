package net.blueshell.api.vote.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface VoteBallotRepository : BaseRepository<VoteBallot, Long> {
    fun findByVoteIdAndUserId(
        voteId: Long,
        userId: Long,
    ): VoteBallot?

    /** Every pick cast in the vote, ballot by ballot and best first, without loading the ballots. */
    @Query(
        "select new net.blueshell.api.vote.persistence.CastPick(b.id, p.position, p.candidateId, p.trackId) " +
            "from VoteBallot b join b.picks p where b.voteId = :voteId order by b.id, p.position",
    )
    fun picksOf(voteId: Long): List<CastPick>

    fun countByVoteId(voteId: Long): Long
}
