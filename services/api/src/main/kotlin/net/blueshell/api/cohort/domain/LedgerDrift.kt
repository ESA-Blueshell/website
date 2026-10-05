package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.TargetMemberRepository
import net.blueshell.api.cohort.persistence.TargetReconcileRun
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** A target's drift: people missing from it, people extra on it, and the missing nobody can push. */
data class Drift(
    val missing: Int,
    val extra: Int,
    val unreachable: Int,
)

/**
 * Every target's drift as the ledger stands now, which is what a target's own page lists.
 *
 * A reconcile run stores the counts it found, and a push or a removal by hand changes the ledger
 * without a new run. Counting from the run left the overview and the alerts saying people were
 * missing whom the target's page no longer listed.
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
        /**
         * Who is unreachable is only known to a reconcile, so that count is the newest run's, held
         * to the people still waiting: somebody pushed since is no longer waiting at all.
         */
        fun of(
            targetId: Long,
            newest: TargetReconcileRun?,
        ): Drift {
            val waiting = desired[targetId] ?: 0
            val unreachable = minOf(newest?.unreachable ?: 0, waiting)
            return Drift(missing = waiting - unreachable, extra = strangers[targetId] ?: 0, unreachable = unreachable)
        }
    }
}
