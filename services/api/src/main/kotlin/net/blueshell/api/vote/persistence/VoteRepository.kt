package net.blueshell.api.vote.persistence

import jakarta.persistence.LockModeType
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface VoteRepository : BaseRepository<Vote, Long> {
    fun findByKindAndYear(
        kind: VoteKind,
        year: Int,
    ): Vote?

    fun findFirstByKindOrderByYearDesc(kind: VoteKind): Vote?

    /** The votes whose reveal is due and that nobody has counted yet. */
    fun findAllByPublishedAtIsNullAndRevealAtLessThanEqual(now: Instant): List<Vote>

    /** The vote, locked until the transaction ends, so two pods cannot count it at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vote v where v.id = :id")
    fun lockById(id: Long): Vote?
}
