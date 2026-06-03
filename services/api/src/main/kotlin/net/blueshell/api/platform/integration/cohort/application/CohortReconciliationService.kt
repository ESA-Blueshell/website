package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortMemberRepository
import net.blueshell.api.platform.integration.cohort.port.`in`.CohortReconciliation
import net.blueshell.api.platform.integration.cohort.port.`in`.SyncCohortMembershipIntent
import net.blueshell.api.shared.job.CohortJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Application implementation of the [CohortReconciliation] inbound
 * port. Bulk operations fan their work out through per-user /
 * per-cohort jobs so a single failure stays isolated in its own
 * JobExecution row with its own retry budget.
 */
@Service
class CohortReconciliationService(
    private val periods: ContributionPeriodService,
    private val users: UserService,
    private val cohortMembers: CohortMemberRepository,
    private val contributionPeriodCohorts: ContributionPeriodCohortResolver,
    private val evaluator: CohortRuleEvaluator,
    private val jobs: TrackedJobDispatcher,
) : CohortReconciliation {

    @Transactional
    override fun evaluateUserCohorts(userId: Long) {
        evaluator.evaluate(userId)
    }

    @Transactional
    override fun reconcileAllContributionPeriodCohorts() {
        val all = periods.findAll()
        log.info("Reconciling cohort + rule for {} contribution periods", all.size)
        all.forEach { period ->
            val periodId = period.id ?: return@forEach
            runCatching { contributionPeriodCohorts.materialize(periodId) }
                .onFailure { e ->
                    log.warn("Failed to materialize cohort for period {}: {}", periodId, e.message)
                }
        }
    }

    @Transactional
    override fun reconcileAllUserCohorts() {
        val all = users.findAll()
        log.info("Enqueueing per-user cohort evaluation for {} users", all.size)
        all.forEach { user ->
            val userId = user.id ?: return@forEach
            runCatching {
                jobs.enqueue(
                    CohortJobs.EvaluateUserCohorts,
                    CohortJobs.EvaluateUserCohortsPayload(userId),
                )
            }.onFailure { e ->
                log.error("Failed to enqueue evaluation for user {}: {}", userId, e.message)
            }
        }
    }

    @Transactional
    override fun resyncCohort(cohortId: Long) {
        val members = cohortMembers.findAllByCohortIdAndUserIdIsNotNull(cohortId)
        if (members.isEmpty()) {
            log.info("Resync for cohort {} has no active desired members", cohortId)
            return
        }
        log.info("Resyncing cohort {} ({} desired members) by re-enqueuing ADD jobs", cohortId, members.size)
        members.forEach { member ->
            jobs.enqueue(
                CohortJobs.SyncCohortMembership,
                CohortJobs.SyncCohortMembershipPayload(
                    userId = member.userId!!,
                    cohortId = cohortId,
                    intent = SyncCohortMembershipIntent.ADD,
                ),
            )
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(CohortReconciliationService::class.java)
    }
}
