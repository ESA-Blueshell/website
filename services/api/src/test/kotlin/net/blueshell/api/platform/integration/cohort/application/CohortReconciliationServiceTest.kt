package net.blueshell.api.platform.integration.cohort.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.cohort.persistence.CohortMember
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortMemberRepository
import net.blueshell.api.platform.integration.cohort.port.`in`.SyncCohortMembershipIntent
import net.blueshell.api.shared.job.CohortJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.junit.jupiter.api.Test

class CohortReconciliationServiceTest {

    private val periods: ContributionPeriodService = mockk()
    private val users: UserService = mockk()
    private val cohortMembers: CohortMemberRepository = mockk()
    private val resolver: ContributionPeriodCohortResolver = mockk(relaxed = true)
    private val evaluator: CohortRuleEvaluator = mockk(relaxed = true)
    private val jobs: TrackedJobDispatcher = mockk(relaxed = true)
    private val service = CohortReconciliationService(
        periods, users, cohortMembers, resolver, evaluator, jobs,
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

    @Test
    fun `resyncCohort enqueues one ADD job per active member`() {
        every { cohortMembers.findAllByCohortIdAndUserIdIsNotNull(99L) } returns listOf(
            member(userId = 1L), member(userId = 2L),
        )

        service.resyncCohort(99L)

        verify {
            jobs.enqueue(
                CohortJobs.SyncCohortMembership,
                CohortJobs.SyncCohortMembershipPayload(1L, 99L, SyncCohortMembershipIntent.ADD),
            )
        }
        verify {
            jobs.enqueue(
                CohortJobs.SyncCohortMembership,
                CohortJobs.SyncCohortMembershipPayload(2L, 99L, SyncCohortMembershipIntent.ADD),
            )
        }
    }

    @Test
    fun `resyncCohort is a no-op when the cohort has no members`() {
        every { cohortMembers.findAllByCohortIdAndUserIdIsNotNull(99L) } returns emptyList()

        service.resyncCohort(99L)

        verify(exactly = 0) {
            jobs.enqueue(CohortJobs.SyncCohortMembership, any<CohortJobs.SyncCohortMembershipPayload>())
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

    private fun member(userId: Long): CohortMember {
        val m = mockk<CohortMember>()
        every { m.userId } returns userId
        return m
    }
}
