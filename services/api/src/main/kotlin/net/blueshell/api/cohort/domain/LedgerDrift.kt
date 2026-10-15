package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.TargetMemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** A target's drift: people missing from it and people extra on it. */
data class Drift(
    val missing: Int,
    val extra: Int,
)

/**
 * Every target's drift as the ledger stands now, which is what a target's own page lists. Counted
 * from the ledger rather than the newest run: a push or a removal by hand changes the ledger
 * without a run.
 */
@Service
class LedgerDrift(
    private val members: TargetMemberRepository,
) {
    /** Reads the ledger once; ask the answer for each target. */
    @Transactional(readOnly = true)
    fun now(): Now =
        Now(
            desired = members.countDesiredByTarget().associate { it.targetId to it.people.toInt() },
            strangers = members.countStrangersByTarget().associate { it.targetId to it.people.toInt() },
        )

    class Now(
        private val desired: Map<Long, Int>,
        private val strangers: Map<Long, Int>,
    ) {
        fun of(targetId: Long): Drift = Drift(missing = desired[targetId] ?: 0, extra = strangers[targetId] ?: 0)
    }
}
