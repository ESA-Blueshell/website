package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.cohort.persistence.Target
import net.blueshell.api.cohort.persistence.TargetKind
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.shared.job.NonRetryableJobException
import net.blueshell.api.testsupport.Entities
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.web.server.ResponseStatusException
import java.util.Optional

/**
 * Unit test for [CohortTargetingService]. The strategy edges,
 * CohortTargetIds, job dispatcher) are the seams — no Spring context.
 * A no-op transaction manager runs the TransactionTemplate callbacks inline.
 */
class CohortTargetingServiceTest {
    private val targetRepo = mock<TargetRepository>()
    private val cohortRepo = mock<CohortRepository>()
    private val targetExternalIds = mock<CohortTargetIds>()
    private val jobs = mock<JobQueue>()
    private val strategy = mock<TargetStrategy>()
    private val strategies: TargetStrategies

    private val txManager =
        object : PlatformTransactionManager {
            override fun getTransaction(definition: TransactionDefinition?): TransactionStatus = SimpleTransactionStatus()

            override fun commit(status: TransactionStatus) {}

            override fun rollback(status: TransactionStatus) {}
        }

    private val service: CohortTargetingService

    init {
        whenever(strategy.system).thenReturn(TargetSystem.BREVO)
        whenever(strategy.descriptor).thenReturn(brevoDescriptor)
        whenever(strategy.resolve(any())).thenReturn(null)
        whenever(strategy.available()).thenReturn(true)
        strategies = TargetStrategies(listOf(strategy))
        service = CohortTargetingService(targetRepo, cohortRepo, targetExternalIds, strategies, jobs, txManager)
    }

    @Test
    fun `create does not touch the provider when the cohort already maps the system`() {
        whenever(cohortRepo.findById(1L)).thenReturn(Optional.of(Entities.cohort(id = 1L)))
        val linked = Entities.target()
        whenever(targetRepo.findByCohortIdAndSystem(1L, "BREVO")).thenReturn(linked)
        whenever(targetExternalIds.find(linked)).thenReturn("list-1")

        val refused =
            assertThrows<ResponseStatusException> {
                service.create(1L, TargetSystem.BREVO, "Members", null)
            }
        assert(refused.reason!!.contains("switch it instead"))

        verify(strategy, never()).create(any(), any())
        verify(targetRepo, never()).save(any())
    }

    @Test
    fun `create materialises the target and records the id`() {
        val saved = Entities.target(id = 42L)
        whenever(cohortRepo.findById(1L)).thenReturn(Optional.of(Entities.cohort(id = 1L)))
        whenever(targetRepo.findByCohortIdAndSystem(1L, "BREVO")).thenReturn(null)
        whenever(strategy.create("Members", "Lists")).thenReturn(external("999", "Members", "Lists"))
        whenever(targetRepo.save(any<Target>())).thenReturn(saved)

        val row = service.create(1L, TargetSystem.BREVO, "Members", "Lists")

        verify(strategy).create("Members", "Lists")
        verify(targetExternalIds).record(saved, "999")
        assert(row.externalId == "999")
    }

    @Test
    fun `create fills a registered target that has no list yet, in its type's folder`() {
        val unlinked = Entities.target(id = 42L, system = "BREVO", label = "Paid 2026-2027", folder = "Contribution paid")
        whenever(cohortRepo.findById(1L)).thenReturn(Optional.of(Entities.cohort(id = 1L)))
        whenever(targetRepo.findByCohortIdAndSystem(1L, "BREVO")).thenReturn(unlinked)
        whenever(targetRepo.findById(42L)).thenReturn(Optional.of(unlinked))
        whenever(strategy.create("Paid 2026-2027", "Contribution paid")).thenReturn(external("999", "Paid 2026-2027", "Contribution paid"))

        val row = service.create(1L, TargetSystem.BREVO, "Paid 2026-2027", null)

        verify(targetRepo, never()).save(any())
        verify(targetExternalIds).record(unlinked, "999")
        assert(row.externalId == "999")
    }

    @Test
    fun `createFor claims the cohort, makes its target in its folder and reconciles it`() {
        val target = Entities.target(id = 7L, system = "BREVO", label = "Paid 2026-2027", folder = "Contribution paid")
        whenever(targetRepo.findById(7L)).thenReturn(Optional.of(target))
        whenever(strategy.create("Paid 2026-2027", "Contribution paid")).thenReturn(external("55", "Paid 2026-2027", "Contribution paid"))

        val ref = service.createFor(7L)

        assert(ref.externalId == "55")
        assert(target.targetClaimedAt != null)
        verify(strategy, never()).catalog(anyOrNull())
        verify(targetExternalIds).record(target, "55")
        verify(jobs).runAsync(
            eq(CohortJobs.ReconcileList),
            eq(CohortJobs.ReconcileListPayload(7L, JobTrigger.ANOTHER_JOB)),
            eq(JobTrigger.ANOTHER_JOB),
            anyOrNull(),
        )
    }

    @Test
    fun `a retried createFor finds the target its first run made instead of making a second`() {
        val target =
            Entities.target(id = 7L, system = "BREVO", label = "Paid 2026-2027", folder = "Contribution paid").apply {
                targetClaimedAt = java.time.Instant.parse("2026-09-29T10:00:00Z")
            }
        whenever(targetRepo.findById(7L)).thenReturn(Optional.of(target))
        whenever(strategy.catalog("Paid 2026-2027")).thenReturn(
            listOf(external("54", "Paid 2026-2027", "Members"), external("55", "Paid 2026-2027", "Contribution paid")),
        )

        val ref = service.createFor(7L)

        assert(ref.externalId == "55")
        verify(strategy, never()).create(any(), anyOrNull())
        verify(targetExternalIds).record(target, "55")
    }

    @Test
    fun `createFor is a no-op when the target already exists`() {
        val target = Entities.target(id = 7L, system = "BREVO", label = "Members")
        whenever(targetRepo.findById(7L)).thenReturn(Optional.of(target))
        whenever(targetExternalIds.find(target)).thenReturn("existing")

        val ref = service.createFor(7L)

        assert(ref.externalId == "existing")
        verify(strategy, never()).create(any(), anyOrNull())
        verify(targetExternalIds, never()).record(any(), any())
    }

    @Test
    fun `createFor fails terminally for a cohort that is gone`() {
        whenever(targetRepo.findById(7L)).thenReturn(Optional.empty())

        assertThrows<NonRetryableJobException> { service.createFor(7L) }
    }

    @Test
    fun `createMissing queues one create-target job per cohort without a target`() {
        whenever(targetRepo.findAllByCohortIdIsNotNullAndExternalIdIsNull()).thenReturn(
            listOf(Entities.target(id = 3L), Entities.target(id = 4L)),
        )

        assert(service.createMissing() == 2)

        verify(jobs).runAsync(
            eq(CohortJobs.CreateCohortTarget),
            eq(CohortJobs.CreateCohortTargetPayload(3L)),
            eq(JobTrigger.ANOTHER_JOB),
            anyOrNull(),
        )
        verify(jobs).runAsync(
            eq(CohortJobs.CreateCohortTarget),
            eq(CohortJobs.CreateCohortTargetPayload(4L)),
            eq(JobTrigger.ANOTHER_JOB),
            anyOrNull(),
        )
    }

    @Test
    fun `linkExisting fills an existing unbound mapping`() {
        val cohort = mock<net.blueshell.api.cohort.persistence.Cohort>()
        val target =
            Entities.target(id = 7L, externalId = null)
        whenever(cohortRepo.findById(1L)).thenReturn(Optional.of(cohort))
        whenever(targetRepo.findByCohortIdAndSystem(1L, "BREVO")).thenReturn(target)

        val row = service.linkExisting(1L, TargetSystem.BREVO, "list-123")

        verify(strategy).resolve("list-123")
        verify(targetRepo, never()).save(any())
        verify(targetExternalIds).record(target, "list-123")
        assert(row.target == target)
        assert(row.externalId == "list-123")
    }

    @Test
    fun `linkExisting allows ids that are not present in the catalog`() {
        val cohort = Cohort(CohortType.NEWSLETTER_SUBSCRIBERS, "Members")
        val saved = Entities.target(id = 7L)
        whenever(cohortRepo.findById(1L)).thenReturn(Optional.of(cohort))
        whenever(targetRepo.findByCohortIdAndSystem(1L, "BREVO")).thenReturn(null)
        whenever(targetRepo.save(any<Target>())).thenReturn(saved)
        whenever(strategy.resolve("missing-list")).thenReturn(null)

        service.linkExisting(1L, TargetSystem.BREVO, "missing-list")

        verify(strategy).resolve("missing-list")
        verify(targetExternalIds).record(saved, "missing-list")
    }

    @Test
    fun `switch enqueues delete-previous and reconcile when asked`() {
        val target =
            Entities.target(system = "BREVO", cohortId = 1L)
        whenever(targetRepo.findById(7L)).thenReturn(Optional.of(target))
        whenever(targetExternalIds.find(target)).thenReturn("old-list")

        service.switchTarget(1L, 7L, "new-list", deletePrevious = true, reconcileNow = true)

        verify(strategy).resolve("new-list")
        verify(targetExternalIds).record(target, "new-list")
        verify(jobs).runAsync(
            eq(CohortJobs.DeleteExternalTarget),
            eq(CohortJobs.DeleteExternalTargetPayload("BREVO", "old-list")),
            eq(JobTrigger.SITE_ACTION),
            anyOrNull(),
        )
        verify(jobs).runAsync(
            eq(CohortJobs.ReconcileList),
            eq(CohortJobs.ReconcileListPayload(7L, JobTrigger.SITE_ACTION)),
            eq(JobTrigger.SITE_ACTION),
            anyOrNull(),
        )
    }

    @Test
    fun `switch does not enqueue a delete when there is no previous target`() {
        val target =
            Entities.target(system = "BREVO", cohortId = 1L)
        whenever(targetRepo.findById(7L)).thenReturn(Optional.of(target))
        whenever(targetExternalIds.find(target)).thenReturn(null)

        service.switchTarget(1L, 7L, "new-list", deletePrevious = true, reconcileNow = false)

        verify(jobs, never()).runAsync(eq(CohortJobs.DeleteExternalTarget), any(), any(), anyOrNull())
        verify(jobs, never()).runAsync(eq(CohortJobs.ReconcileList), any(), any(), anyOrNull())
    }

    @Test
    fun `switch rejects a cohort that is not a target of the path cohort`() {
        val target = Entities.target(cohortId = 99L)
        whenever(targetRepo.findById(7L)).thenReturn(Optional.of(target))

        assertThrows<ResponseStatusException> {
            service.switchTarget(1L, 7L, "new-list", deletePrevious = false, reconcileNow = false)
        }

        verify(targetExternalIds, never()).record(any(), any())
    }

    @Test
    fun `linking a cohort already linked on the system, or a cohort that is gone, is refused`() {
        whenever(cohortRepo.findById(1L)).thenReturn(Optional.of(Entities.cohort(id = 1L)))
        val linked = Entities.target(id = 3L)
        whenever(targetRepo.findByCohortIdAndSystem(1L, "BREVO")).thenReturn(linked)
        whenever(targetExternalIds.find(linked)).thenReturn("list-1")
        whenever(cohortRepo.findById(2L)).thenReturn(Optional.empty())

        val linkedAlready = assertThrows<ResponseStatusException> { service.linkExisting(1L, TargetSystem.BREVO, "list-2") }
        val gone = assertThrows<ResponseStatusException> { service.linkExisting(2L, TargetSystem.BREVO, "list-2") }

        assert(linkedAlready.reason == "Cohort 1 already has a BREVO target")
        assert(gone.reason == "Cohort 2 not found")
    }

    @Test
    fun `switching a target that is gone is refused`() {
        whenever(targetRepo.findById(9L)).thenReturn(Optional.empty())

        val refused = assertThrows<ResponseStatusException> { service.switchTarget(1L, 9L, "list-2", false, false) }

        assert(refused.reason == "Target 9 not found")
    }

    @Test
    fun `deleteTarget calls the provider`() {
        service.deleteTarget(TargetSystem.BREVO, "stale-list")

        verify(strategy).delete(external("stale-list", "stale-list", null))
    }

    private fun external(
        id: String,
        label: String,
        folder: String?,
    ) = ExternalTarget(TargetSystem.BREVO, id, TargetKind.LIST, label, folder)

    private companion object {
        val brevoDescriptor =
            TargetDescriptor(
                system = TargetSystem.BREVO,
                kind = TargetKind.LIST,
            )
    }

    @Test
    fun `create refuses a system that cannot be reached before anything is made`() {
        whenever(strategy.available()).thenReturn(false)
        whenever(cohortRepo.findById(1L)).thenReturn(Optional.of(Entities.cohort(id = 1L)))

        org.assertj.core.api.Assertions
            .assertThatThrownBy { service.create(1L, TargetSystem.BREVO, "Members", null) }
            .isInstanceOf(TargetSystemUnavailable::class.java)
        org.mockito.kotlin.verify(strategy, org.mockito.kotlin.never()).create(any(), anyOrNull())
    }
}
