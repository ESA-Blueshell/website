package net.blueshell.api.platform.integration.cohort.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.platform.integration.cohort.application.definition.CohortDefinition
import net.blueshell.api.platform.integration.cohort.application.definition.CohortDefinitionRegistry
import net.blueshell.api.platform.integration.cohort.application.definition.CohortMembershipUpdater
import net.blueshell.api.platform.integration.cohort.application.definition.CohortRegistrar
import net.blueshell.api.platform.integration.cohort.application.definition.RegistrationReport
import net.blueshell.api.platform.integration.cohort.application.CohortJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.junit.jupiter.api.Test

class CohortReconciliationServiceTest {

    private val users: UserService = mockk()
    private val definitions: CohortDefinitionRegistry = mockk()
    private val registrar: CohortRegistrar = mockk(relaxed = true)
    private val updater: CohortMembershipUpdater = mockk(relaxed = true)
    private val jobs: TrackedJobDispatcher = mockk(relaxed = true)
    private val service = CohortReconciliationService(
        users, definitions, registrar, updater, jobs,
        // A relaxed manager runs the per-page TransactionTemplate callbacks inline.
        transactionManager = mockk(relaxed = true),
    )

    private val pageSize = CohortReconciliationService.PAGE_SIZE

    @Test
    fun `evaluateUserCohorts reconciles that one member`() {
        service.evaluateUserCohorts(42L)

        verify { updater.updateMember(42L) }
    }

    @Test
    fun `the sweep registers the definitions and then recomputes each one`() {
        val first = definition("PERIOD_MEMBERS:1")
        val second = definition("PERIOD_MEMBERS:2")
        every { registrar.register() } returns RegistrationReport(2, 0, 0, emptyList())
        every { definitions.all() } returns listOf(first, second)

        service.reconcileAllContributionPeriodCohorts()

        verify { registrar.register() }
        verify { updater.updateCohort(first) }
        verify { updater.updateCohort(second) }
    }

    @Test
    fun `one cohort failing to recompute does not stop the others`() {
        val first = definition("PERIOD_MEMBERS:1")
        val broken = definition("PERIOD_MEMBERS:2")
        val last = definition("PERIOD_MEMBERS:7")
        every { registrar.register() } returns RegistrationReport(3, 0, 0, emptyList())
        every { definitions.all() } returns listOf(first, broken, last)
        every { updater.updateCohort(broken) } throws RuntimeException("kaboom")

        service.reconcileAllContributionPeriodCohorts()

        verify { updater.updateCohort(first) }
        verify { updater.updateCohort(last) }
    }

    private fun definition(key: String): CohortDefinition = mockk<CohortDefinition>().also {
        every { it.key } returns key
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
}
