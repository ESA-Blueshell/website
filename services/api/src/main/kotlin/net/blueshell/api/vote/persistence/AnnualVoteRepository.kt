package net.blueshell.api.vote.persistence

import jakarta.persistence.LockModeType
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface AnnualVoteRepository : BaseRepository<AnnualVote, Long> {
    fun findByKindAndYear(
        kind: VoteKind,
        year: Int,
    ): AnnualVote?

    fun findFirstByKindOrderByYearDesc(kind: VoteKind): AnnualVote?

    /** The votes whose reveal is due and that nobody has counted yet. */
    fun findAllByPublishedAtIsNullAndRevealAtLessThanEqual(now: Instant): List<AnnualVote>

    /** The vote, locked until the transaction ends, so two pods cannot count it at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from AnnualVote v where v.id = :id")
    fun lockById(id: Long): AnnualVote?
}
