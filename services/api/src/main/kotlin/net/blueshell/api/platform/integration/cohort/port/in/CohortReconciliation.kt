package net.blueshell.api.platform.integration.cohort.port.`in`

/**
 * Inbound (driving) port for cohort reconciliation use cases — the
 * "kick the engine" operations an admin reaches for when verifying
 * the system or recovering from drift.
 *
 * Every method is idempotent and safe to call repeatedly; the
 * bulk operations fan their work out through per-user / per-cohort
 * jobs rather than doing it inline.
 */
interface CohortReconciliation {

    /**
     * Re-evaluates one user's cohort membership against the current
     * rules. Desired-row writes happen synchronously; external state
     * converges through per-member `cohort.membership-sync` ADD jobs
     * enqueued for each cohort the user joins. Cohorts the user leaves are
     * soft-deleted locally and surface as external extras during drift
     * reconciliation.
     */
    fun evaluateUserCohorts(userId: Long)

    /**
     * Walks every active `ContributionPeriod` and ensures its three period
     * cohorts — contribution-paid, members and active-members — and their
     * subjects exist. No-op for periods already provisioned.
     */
    fun reconcileAllContributionPeriodCohorts()

    /**
     * Re-evaluates every user's cohort membership by enqueuing one
     * per-user evaluation job. Each per-user job runs in its own
     * JobExecution row with its own retry budget, so a single
     * user's adapter failure stays isolated.
     */
    fun reconcileAllUserCohorts()
}
