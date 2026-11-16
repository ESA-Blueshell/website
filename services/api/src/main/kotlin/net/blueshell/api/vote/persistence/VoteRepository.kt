package net.blueshell.api.vote.persistence

import net.blueshell.api.shared.repository.BaseRepository
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
}
