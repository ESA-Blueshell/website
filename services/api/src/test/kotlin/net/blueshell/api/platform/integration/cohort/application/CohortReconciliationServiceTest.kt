package net.blueshell.api.platform.integration.cohort.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.User
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
    )

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
    fun `reconcileAllUserCohorts enqueues one EvaluateUserCohorts per user`() {
        every { users.findAll() } returns mutableListOf(user(10L), user(11L))

        service.reconcileAllUserCohorts()

        verify {
            jobs.enqueue(
                CohortJobs.EvaluateUserCohorts,
                CohortJobs.EvaluateUserCohortsPayload(10L),
            )
        }
        verify {
            jobs.enqueue(
                CohortJobs.EvaluateUserCohorts,
                CohortJobs.EvaluateUserCohortsPayload(11L),
            )
        }
    }

    private fun period(id: Long): ContributionPeriod {
        val p = mockk<ContributionPeriod>()
        every { p.id } returns id
        return p
    }

    private fun user(id: Long): User {
        val u = mockk<User>()
        every { u.id } returns id
        return u
    }
}
