package net.blueshell.api.vote.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface VoteRepository : BaseRepository<Vote, Long> {
    fun findByKindAndYear(
        kind: VoteKind,
        year: Int,
    ): Vote?

    /** The vote of [kind] that opened last, by [now]. */
    fun findFirstByKindAndOpensAtLessThanEqualOrderByOpensAtDesc(
        kind: VoteKind,
        now: Instant,
    ): Vote?

    /** The vote of [kind] that opens first, for a kind none of whose votes has opened yet. */
    fun findFirstByKindOrderByOpensAtAsc(kind: VoteKind): Vote?

    /** Whether any vote takes ballots at [now]: open, not yet closed, and not revealed. */
    @Query("select count(v) > 0 from Vote v where v.opensAt <= :now and v.closesAt > :now and v.revealedAt is null")
    fun existsVotingAt(now: Instant): Boolean

    /** The votes whose reveal is due and that nobody has counted yet. */
    fun findAllByRevealedAtIsNullAndRevealAtLessThanEqual(now: Instant): List<Vote>
}
