package net.blueshell.api.cohort.domain

import net.blueshell.api.jobs.api.AbstractJsonJobHandler
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.job.JobCount
import net.blueshell.api.shared.job.JobDefinition
import net.blueshell.api.shared.job.JobTally
import net.blueshell.api.shared.job.NonRetryableJobException
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

/**
 * Every cohort job in one place: a [JobDefinition] and the call that runs its payload.
 *
 * Each binding is a bean because the queue finds handlers by injecting every [AbstractJsonJobHandler];
 * `CohortJobHandlersTest` fails if a definition here has no binding.
 */
@Configuration
class CohortJobHandlers(
    private val objectMapper: ObjectMapper,
    private val reconciliation: CohortReconciliationService,
    private val membership: CohortMembershipSyncService,
    private val targeting: CohortTargeting,
    private val remediation: CohortRemediation,
    private val inbound: InboundReconcile,
) {
    @Bean
    fun syncCohortMembershipHandler() =
        bindSkipping(CohortJobs.SyncCohortMembership) {
            membership.sync(it.userId, it.cohortId, it.intent)
        }

    @Bean
    fun evaluateUserCohortsHandler() =
        bind(CohortJobs.EvaluateUserCohorts) {
            reconciliation.evaluateUserCohorts(it.userId)
        }

    @Bean
    fun reconcileAllUserCohortsHandler() =
        bindCounting(CohortJobs.ReconcileAllUserCohorts) {
            listOf(JobCount(JobTally.USERS_QUEUED, reconciliation.reconcileAllUserCohorts().toLong()))
        }

    @Bean
    fun reconcileAllContributionPeriodCohortsHandler() =
        bindCounting(CohortJobs.ReconcileAllContributionPeriodCohorts) {
            val swept = reconciliation.reconcileAllContributionPeriodCohorts()
            listOf(
                JobCount(JobTally.COHORTS_RECOMPUTED, swept.recomputed.toLong()),
                JobCount(JobTally.COHORTS_FAILED, swept.failed.toLong()),
            )
        }

    @Bean
    fun reconcileListHandler() =
        bind(CohortJobs.ReconcileList) {
            remediation.verifyCohort(it.cohortId)
        }

    @Bean
    fun removeExternalMemberHandler() =
        bind(CohortJobs.RemoveExternalMember) {
            remediation.removeExternalMember(it.cohortId, it.externalUserId)
        }

    @Bean
    fun deleteExternalTargetHandler() =
        bind(CohortJobs.DeleteExternalTarget) {
            targeting.deleteTarget(targetSystem(it.system), it.externalTargetId)
        }

    @Bean
    fun materializeCohortTargetHandler() =
        bind(CohortJobs.MaterializeCohortTarget) {
            targeting.materialize(it.cohortId)
        }

    @Bean
    fun applyInboundReconcileHandler() =
        bind(CohortJobs.ApplyInboundReconcile) {
            inbound.applyJob(it)
        }

    private fun <T : Any> bind(
        definition: JobDefinition<T>,
        perform: (T) -> Unit,
    ): CohortJobBinding<T> =
        bindSkipping(definition) {
            perform(it)
            null
        }

    // [perform] answers why the run did nothing, or null where it did its work.
    private fun <T : Any> bindSkipping(
        definition: JobDefinition<T>,
        perform: (T) -> String?,
    ): CohortJobBinding<T> = CohortJobBinding(objectMapper, definition) { CohortJobResult(skipped = perform(it)) }

    // [perform] answers what the run counted about its work.
    private fun <T : Any> bindCounting(
        definition: JobDefinition<T>,
        perform: (T) -> List<JobCount>,
    ): CohortJobBinding<T> = CohortJobBinding(objectMapper, definition) { CohortJobResult(counts = perform(it)) }

    /** A payload naming a system that no longer exists will never parse, however often it is retried. */
    private fun targetSystem(name: String): TargetSystem =
        runCatching { TargetSystem.valueOf(name) }
            .getOrElse { throw NonRetryableJobException("Job payload names unknown target system '$name'") }
}

/**
 * Runs one cohort job: the definition supplies the type and payload class, the lambda the work.
 *
 * Open because [AbstractJsonJobHandler.handle] is `@Transactional` and Spring subclasses this to
 * proxy it; the class carries no annotation for the Kotlin Spring plugin to open it on.
 */
open class CohortJobBinding<T : Any>(
    objectMapper: ObjectMapper,
    definition: JobDefinition<T>,
    private val perform: (T) -> CohortJobResult,
) : AbstractJsonJobHandler<T>(objectMapper, definition) {
    override fun handlePayload(payload: T) {
        val result = perform(payload)
        result.skipped?.let(::skip)
        result.counts.forEach { counted(it.tally, it.count, it.channels, it.names) }
    }
}

/** Why a cohort job did nothing, or what it counted about its work. */
data class CohortJobResult(
    val skipped: String? = null,
    val counts: List<JobCount> = emptyList(),
)
