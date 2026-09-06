package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortMember
import net.blueshell.api.cohort.persistence.CohortSubject
import net.blueshell.api.cohort.persistence.CohortMemberRepository
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortSubjectRepository
import net.blueshell.api.shared.job.JobQueue
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
    private val subjects: CohortSubjectRepository,
    private val cohorts: CohortRepository,
    private val memberships: CohortMemberRepository,
    private val jobs: JobQueue,
) {
    /** Reconciles one member against every cohort. */
    @Transactional
    fun updateMember(userId: Long): MembershipChange {
        val belongsTo = definitions.definitionsFor(userId).mapNotNullTo(mutableSetOf()) { subjectIdFor(it) }
        val current = memberships.findAllByUserIdAndUserIdIsNotNull(userId)
        val currentBySubject = current.groupBy { it.subject.id }

        val toAdd = belongsTo - currentBySubject.keys.filterNotNull().toSet()
        val toRemove = currentBySubject.keys.filterNotNull().toSet() - belongsTo

        toAdd.forEach { subjectId -> cohortsOf(subjectId).forEach { add(it, userId) } }
        toRemove.forEach { subjectId ->
            currentBySubject[subjectId].orEmpty().forEach { remove(it) }
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
        val subjectId = subjectIdFor(definition) ?: return MembershipChange(null, emptySet(), emptySet())
        val desired = definitions.membersOf(definition)
        val present = memberships.findAllBySubjectIdAndUserIdIsNotNull(subjectId)
        val presentIds = present.mapNotNullTo(mutableSetOf()) { it.userId }

        val joining = desired - presentIds
        val leaving = presentIds - desired

        cohortsOf(subjectId).forEach { cohort -> joining.forEach { add(cohort, it) } }
        present.filter { it.userId in leaving }.forEach { remove(it) }

        if (joining.isNotEmpty() || leaving.isNotEmpty()) {
            log.info("[cohort] {} joined={} left={}", definition.key, joining.size, leaving.size)
        }
        return MembershipChange(null, joining, leaving)
    }

    private fun subjectIdFor(definition: CohortDefinition): Long? =
        subjects.findByDefinitionKey(definition.key)?.id

    private fun cohortsOf(subjectId: Long): List<Cohort> = cohorts.findAllBySubjectId(subjectId)

    private fun add(cohort: Cohort, userId: Long) {
        val subject: CohortSubject = subjects.findById(cohort.subjectId!!).orElseThrow {
            IllegalStateException("Cohort ${cohort.id} names a subject that is not there")
        }
        memberships.save(CohortMember(cohort = cohort, userId = userId, subject = subject))
        jobs.runAsync(
            CohortJobs.SyncCohortMembership,
            CohortJobs.SyncCohortMembershipPayload(userId, cohort.id!!, SyncCohortMembershipIntent.ADD),
        )
    }

    private fun remove(member: CohortMember) {
        val userId = member.userId ?: return
        val cohortId = member.cohort.id ?: return
        memberships.delete(member) // soft delete: the row is kept for historical statistics
        jobs.runAsync(
            CohortJobs.SyncCohortMembership,
            CohortJobs.SyncCohortMembershipPayload(userId, cohortId, SyncCohortMembershipIntent.REMOVE),
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(CohortMembershipUpdater::class.java)
    }
}

/** What one reconciliation moved: subject ids for a member, member ids for a cohort. */
data class MembershipChange(
    val userId: Long?,
    val joined: Set<Long>,
    val left: Set<Long>,
) {
    val isNoOp: Boolean get() = joined.isEmpty() && left.isEmpty()
}
