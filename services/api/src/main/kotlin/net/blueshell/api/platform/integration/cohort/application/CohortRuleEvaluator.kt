package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.platform.integration.cohort.persistence.Cohort
import net.blueshell.api.platform.integration.cohort.persistence.CohortMember
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortMemberRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRuleRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortSubjectRepository
import net.blueshell.api.platform.integration.cohort.port.`in`.SyncCohortMembershipIntent
import net.blueshell.api.shared.job.CohortJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Decides which cohorts a user should belong to, based on the
 * [UserFact]s currently true for them and the enabled
 * [CohortRule][net.blueshell.api.platform.integration.cohort.persistence.CohortRule]s,
 * and reconciles the user's `cohort_member` rows against that
 * desired set.
 *
 * Each call:
 *
 * 1. Collects the user's facts via [UserFactCollector].
 * 2. Fetches every enabled rule whose left-hand side matches a held
 *    fact and builds the desired cohort set.
 * 3. Diffs against the user's current `cohort_member` rows.
 * 4. For each cohort to add: inserts a `CohortMember` row and
 *    enqueues `SyncCohortMembership(userId, cohortId, ADD)`.
 * 5. For each cohort to remove: soft-deletes the `CohortMember` row
 *    and enqueues `SyncCohortMembership(userId, cohortId, REMOVE)`.
 *
 * All of the above runs in a single transaction. The job dispatch
 * uses the `afterCommit` hook in `JobDispatcher.enqueue`, so a
 * rollback leaves no half-fired jobs.
 */
@Service
class CohortRuleEvaluator(
    private val userFactCollector: UserFactCollector,
    private val rules: CohortRuleRepository,
    private val memberships: CohortMemberRepository,
    private val cohorts: CohortRepository,
    private val subjects: CohortSubjectRepository,
    private val jobs: TrackedJobDispatcher,
    private val users: UserService,
) {
    @Transactional
    fun evaluate(userId: Long): CohortRuleEvaluation {
        // Soft-deleted users are kept in cohort_member for historical
        // statistics. Skip the diff so the row stays put, and no REMOVE
        // jobs get pushed to external systems on their behalf. Hard-deleted
        // ids fall through to the normal collect → empty-facts path and
        // diff out as removes, which is what we want for genuinely-gone
        // users.
        if (users.isSoftDeleted(userId)) {
            log.debug("[cohort] user={} soft-deleted, skipping evaluation", userId)
            return CohortRuleEvaluation(userId, emptySet(), emptySet(), emptySet())
        }
        val facts = userFactCollector.collect(userId)
        val desired = facts.flatMap { fact ->
            rules.findAllByFactKindAndFactKeyAndEnabledTrue(fact.kind, fact.key)
        }.mapNotNull { it.cohort.id }.toSet()
        val currentMemberships = memberships.findAllByUserId(userId)
        val current = currentMemberships.mapNotNull { it.cohort.id }.toSet()

        val evaluation = CohortRuleEvaluation(userId, facts, desired, current)
        if (evaluation.isNoOp) {
            log.debug(
                "[cohort] user={} facts={} cohorts unchanged (membership={})",
                userId, facts.size, current,
            )
            return evaluation
        }

        evaluation.toAdd.forEach { cohortId -> add(userId, cohortId) }
        evaluation.toRemove.forEach { cohortId ->
            removeExistingMembership(currentMemberships, cohortId, userId)
        }

        log.info(
            "[cohort] user={} facts={} added={} removed={}",
            userId, facts.size, evaluation.toAdd, evaluation.toRemove,
        )
        return evaluation
    }

    private fun add(userId: Long, cohortId: Long) {
        val cohort: Cohort = cohorts.findById(cohortId).orElseThrow {
            IllegalStateException("Rule references unknown cohort $cohortId for user $userId")
        }
        val subject = cohort.subjectId?.let { id ->
            subjects.findById(id).orElseThrow {
                IllegalStateException("Cohort $cohortId references unknown subject $id")
            }
        } ?: error(
            "Cohort $cohortId has no subject_id; V72 backfill should have populated it. Refusing to insert orphan member.",
        )
        memberships.save(CohortMember(cohort = cohort, userId = userId, subject = subject))
        jobs.enqueue(
            CohortJobs.SyncCohortMembership,
            CohortJobs.SyncCohortMembershipPayload(userId, cohortId, SyncCohortMembershipIntent.ADD),
        )
    }

    private fun removeExistingMembership(
        currentMemberships: List<CohortMember>,
        cohortId: Long,
        userId: Long,
    ) {
        val existing = currentMemberships.first { it.cohort.id == cohortId }
        memberships.delete(existing) // soft-delete via @SQLDelete on CohortMember
        jobs.enqueue(
            CohortJobs.SyncCohortMembership,
            CohortJobs.SyncCohortMembershipPayload(userId, cohortId, SyncCohortMembershipIntent.REMOVE),
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(CohortRuleEvaluator::class.java)
    }
}
