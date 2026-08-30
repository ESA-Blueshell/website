package net.blueshell.api.cohort.domain

import net.blueshell.api.user.api.UserService
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

/**
 * Application implementation of the [CohortReconciliation] inbound port. Bulk operations fan
 * their work out through per-user jobs so a single failure stays isolated in its own
 * JobExecution row with its own retry budget.
 */
@Service
class CohortReconciliationService(
    private val users: UserService,
    private val definitions: CohortDefinitionRegistry,
    private val registrar: CohortRegistrar,
    private val updater: CohortMembershipUpdater,
    private val jobs: TrackedJobDispatcher,
    transactionManager: PlatformTransactionManager,
) : CohortReconciliation {

    // One short transaction per page (REQUIRES_NEW), so each page's child jobs
    // commit and dispatch incrementally instead of the whole scan running under
    // the single transaction AbstractJsonJobHandler opens around the spawn job.
    private val pageTransaction = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    @Transactional
    override fun evaluateUserCohorts(userId: Long) {
        updater.updateMember(userId)
    }

    /**
     * Puts a cohort record behind every definition, then recomputes each one in full.
     *
     * This is the sweep that catches what events cannot: a definition added in code, a period
     * created while the application was down, a member whose facts changed elsewhere.
     */
    @Transactional
    override fun reconcileAllContributionPeriodCohorts() {
        val report = registrar.register()
        log.info(
            "[cohort] {} definitions registered ({} new, {} orphaned)",
            report.total, report.created, report.orphaned.size,
        )
        definitions.all().forEach { definition ->
            runCatching { updater.updateCohort(definition) }
                .onFailure { e -> log.warn("Failed to recompute {}: {}", definition.key, e.message) }
        }
    }

    override fun reconcileAllUserCohorts() {
        var afterId = 0L
        var total = 0
        while (true) {
            // Read + enqueue one page in its own short transaction. A page that
            // commits has dispatched its jobs even if a later page fails, and
            // per-user runCatching keeps one bad enqueue from dropping its page.
            val page = pageTransaction.execute {
                val ids = users.findActiveIdsAfter(afterId, PAGE_SIZE)
                ids.forEach { userId ->
                    runCatching {
                        jobs.runAsync(
                            CohortJobs.EvaluateUserCohorts,
                            CohortJobs.EvaluateUserCohortsPayload(userId),
                        )
                    }.onFailure { e ->
                        log.error("Failed to enqueue evaluation for user {}: {}", userId, e.message)
                    }
                }
                ids
            }
            if (page.isEmpty()) break
            afterId = page.last()
            total += page.size
        }
        log.info("Enqueued per-user cohort evaluation across {} users in pages of {}", total, PAGE_SIZE)
    }

    companion object {
        /** Users per page for the all-users reconcile sweep. */
        const val PAGE_SIZE = 500

        private val log = LoggerFactory.getLogger(CohortReconciliationService::class.java)
    }
}
