package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.TargetMemberRepository
import net.blueshell.api.shared.enums.TargetSystem
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** A target somebody belongs on and is not on, because the account it needs is one they link themselves. */
data class UnlinkedTarget(
    val system: TargetSystem,
    val label: String,
)

@Service
class UnlinkedTargets(
    private val members: TargetMemberRepository,
    private val strategies: TargetStrategies,
) {
    @Transactional(readOnly = true)
    fun of(userId: Long): List<UnlinkedTarget> =
        members
            .findAllByUserIdAndUserIdIsNotNull(userId)
            .groupBy { it.target.system }
            .flatMap { (name, rows) ->
                val system = TargetSystem.entries.firstOrNull { it.name == name } ?: return@flatMap emptyList()
                val strategy = strategies.find(system)
                // A system that makes accounts itself reaches everybody; a linked account reaches them too.
                if (strategy == null || strategy.makesMemberIds || userId in strategy.memberIds(setOf(userId))) {
                    emptyList()
                } else {
                    rows.map { UnlinkedTarget(system, it.target.label) }
                }
            }.distinct()
            .sortedWith(compareBy({ it.system.shownName }, { it.label.lowercase() }))
}
