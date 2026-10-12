package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.Target
import net.blueshell.api.cohort.persistence.TargetMember
import net.blueshell.api.cohort.persistence.TargetMemberRepository
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Brings the membership ledger into line with the definitions, for one member or for one
 * whole cohort.
 *
 * Both directions exist because both questions get asked: something changes about a member and
 * only their rows move, or something changes about a cohort and the whole set is recomputed.
 * The two must agree, which the definitions' own tests assert. Rows are written here and pushed
 * by the per-member sync job.
 */
@Service
class CohortMembershipUpdater(
    private val definitions: CohortDefinitionRegistry,
    private val cohorts: CohortRepository,
    private val targets: TargetRepository,
    private val memberships: TargetMemberRepository,
    private val jobs: JobQueue,
) {
    /** Reconciles one member against every cohort. */
    @Transactional
    fun updateMember(userId: Long): MembershipChange {
        val belongsTo = definitions.definitionsFor(userId).mapNotNullTo(mutableSetOf()) { cohortIdFor(it) }
        val current = memberships.findAllByUserIdAndUserIdIsNotNull(userId)
        val currentByCohort = current.groupBy { it.cohort.id }

        val toAdd = belongsTo - currentByCohort.keys.filterNotNull().toSet()
        val toRemove = currentByCohort.keys.filterNotNull().toSet() - belongsTo

        toAdd.forEach { cohortId -> targetsOf(cohortId).forEach { add(it, userId) } }
        toRemove.forEach { cohortId ->
            currentByCohort[cohortId].orEmpty().forEach { remove(it) }
        }

        if (toAdd.isNotEmpty() || toRemove.isNotEmpty()) {
            log.info("[cohort] user={} joined={} left={}", userId, toAdd, toRemove)
        }
        return MembershipChange(userId, toAdd, toRemove)
    }

    /**
     * Reconciles one cohort against everybody, which is what catches a member whose facts
     * changed without an event ever reaching this side.
     */
    @Transactional
    fun updateCohort(definition: CohortDefinition): MembershipChange {
        val cohortId = cohortIdFor(definition) ?: return MembershipChange(null, emptySet(), emptySet())
        val desired = definitions.membersOf(definition)
        val present = memberships.findAllByCohortIdAndUserIdIsNotNull(cohortId)
        val presentIds = present.mapNotNullTo(mutableSetOf()) { it.userId }

        val joining = desired - presentIds
        val leaving = presentIds - desired

        targetsOf(cohortId).forEach { target -> joining.forEach { add(target, it) } }
        present.filter { it.userId in leaving }.forEach { remove(it) }

        if (joining.isNotEmpty() || leaving.isNotEmpty()) {
            log.info("[cohort] {} joined={} left={}", definition.key, joining.size, leaving.size)
        }
        return MembershipChange(null, joining, leaving)
    }

    private fun cohortIdFor(definition: CohortDefinition): Long? = cohorts.findByDefinitionKey(definition.key)?.id

    private fun targetsOf(cohortId: Long): List<Target> = targets.findAllByCohortId(cohortId)

    private fun add(
        target: Target,
        userId: Long,
    ) {
        val cohort: Cohort =
            cohorts.findById(target.cohortId!!).orElseThrow {
                IllegalStateException("Target ${target.id} names a cohort that is not there")
            }
        memberships.save(TargetMember(target = target, userId = userId, cohort = cohort))
        jobs.runAsync(
            CohortJobs.SyncCohortMembership,
            CohortJobs.SyncCohortMembershipPayload(userId, target.id!!, SyncCohortMembershipIntent.ADD),
            JobTrigger.MEMBERSHIP_CHANGED,
        )
    }

    private fun remove(member: TargetMember) {
        val userId = member.userId ?: return
        val targetId = member.target.id ?: return
        memberships.delete(member) // soft delete: the row is kept for historical statistics
        jobs.runAsync(
            CohortJobs.SyncCohortMembership,
            CohortJobs.SyncCohortMembershipPayload(userId, targetId, SyncCohortMembershipIntent.REMOVE),
            JobTrigger.MEMBERSHIP_CHANGED,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(CohortMembershipUpdater::class.java)
    }
}

/** What one reconciliation moved: cohort ids for a member, member ids for a cohort. */
data class MembershipChange(
    val userId: Long?,
    val joined: Set<Long>,
    val left: Set<Long>,
) {
    val isNoOp: Boolean get() = joined.isEmpty() && left.isEmpty()
}
