package net.blueshell.api.platform.integration.cohort.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.shared.job.CohortJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.junit.jupiter.api.Test

class CohortReconciliationServiceTest {

    private val periods: ContributionPeriodService = mockk()
    private val users: UserService = mockk()
    private val resolver: ContributionPeriodCohortResolver = mockk(relaxed = true)
    private val evaluator: CohortRuleEvaluator = mockk(relaxed = true)
    private val jobs: TrackedJobDispatcher = mockk(relaxed = true)
    private val service = CohortReconciliationService(
        periods, users, resolver, evaluator, jobs,
        // A relaxed manager runs the per-page TransactionTemplate callbacks inline.
        transactionManager = mockk(relaxed = true),
    )

    private val pageSize = CohortReconciliationService.PAGE_SIZE

    @Test
    fun `evaluateUserCohorts delegates to the rule evaluator`() {
        service.evaluateUserCohorts(42L)

        verify { evaluator.evaluate(42L) }
    }

    @Test
    fun `reconcileAllContributionPeriodCohorts calls the resolver for every period`() {
        every { periods.findAll() } returns mutableListOf(period(1L), period(2L), period(7L))

        service.reconcileAllContributionPeriodCohorts()

        verify { resolver.materialize(1L) }
        verify { resolver.materialize(2L) }
        verify { resolver.materialize(7L) }
    }

    @Test
    fun `reconcileAllContributionPeriodCohorts continues past a single materialize failure`() {
        every { periods.findAll() } returns mutableListOf(period(1L), period(2L), period(7L))
        every { resolver.materialize(2L) } throws RuntimeException("kaboom")

        service.reconcileAllContributionPeriodCohorts()

        verify { resolver.materialize(1L) }
        verify { resolver.materialize(2L) }
        verify { resolver.materialize(7L) }
    }

    @Test
    fun `reconcileAllUserCohorts enqueues one EvaluateUserCohorts per user across paged ids`() {
        // Two pages then empty — proves it reads incrementally by keyset rather
        // than loading every user in one query/transaction.
        every { users.findActiveIdsAfter(0L, pageSize) } returns listOf(10L, 11L)
        every { users.findActiveIdsAfter(11L, pageSize) } returns emptyList()

        service.reconcileAllUserCohorts()

        verify { users.findActiveIdsAfter(0L, pageSize) }
        verify { users.findActiveIdsAfter(11L, pageSize) }
        verify { jobs.enqueue(CohortJobs.EvaluateUserCohorts, CohortJobs.EvaluateUserCohortsPayload(10L)) }
        verify { jobs.enqueue(CohortJobs.EvaluateUserCohorts, CohortJobs.EvaluateUserCohortsPayload(11L)) }
    }

    @Test
    fun `reconcileAllUserCohorts keeps going past a failed enqueue and a later page`() {
        every { users.findActiveIdsAfter(0L, pageSize) } returns listOf(10L, 11L)
        every { users.findActiveIdsAfter(11L, pageSize) } returns listOf(12L)
        every { users.findActiveIdsAfter(12L, pageSize) } returns emptyList()
        every {
            jobs.enqueue(CohortJobs.EvaluateUserCohorts, CohortJobs.EvaluateUserCohortsPayload(11L))
        } throws RuntimeException("boom")

        service.reconcileAllUserCohorts()

        // 10 (before the failure) and 12 (a later page) are still enqueued.
        verify { jobs.enqueue(CohortJobs.EvaluateUserCohorts, CohortJobs.EvaluateUserCohortsPayload(10L)) }
        verify { jobs.enqueue(CohortJobs.EvaluateUserCohorts, CohortJobs.EvaluateUserCohortsPayload(12L)) }
    }

    private fun period(id: Long): ContributionPeriod {
        val p = mockk<ContributionPeriod>()
        every { p.id } returns id
        return p
    }
}
