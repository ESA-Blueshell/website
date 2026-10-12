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
        strategies = TargetStrategies(listOf(strategy))
        service = CohortTargetingService(targetRepo, cohortRepo, targetExternalIds, strategies, jobs, txManager)
    }

    @Test
    fun `create does not touch the provider when the cohort already maps the system`() {
        whenever(cohortRepo.findById(1L)).thenReturn(Optional.of(Entities.cohort(id = 1L)))
        whenever(targetRepo.findByCohortIdAndSystem(1L, "BREVO")).thenReturn(Entities.target())

        assertThrows<ResponseStatusException> {
            service.create(1L, TargetSystem.BREVO, "Members", null)
        }

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
    fun `materialize without an existing target fails terminally and never creates a provider target`() {
        val target =
            Entities.target(id = 7L, system = "BREVO", label = "Members", folder = "Committees")
        whenever(targetRepo.findById(7L)).thenReturn(Optional.of(target))
        whenever(targetExternalIds.find(target)).thenReturn(null)

        assertThrows<NonRetryableJobException> {
            service.materialize(7L)
        }

        verify(strategy, never()).create(any(), any())
        verify(targetExternalIds, never()).record(any(), any())
    }

    @Test
    fun `materialize is a no-op when the target already exists`() {
        val target =
            Entities.target(id = 7L, system = "BREVO", label = "Members")
        whenever(targetRepo.findById(7L)).thenReturn(Optional.of(target))
        whenever(targetExternalIds.find(target)).thenReturn("existing")

        val ref = service.materialize(7L)

        assert(ref.externalId == "existing")
        verify(strategy, never()).create(any(), any())
        verify(targetExternalIds, never()).record(any(), any())
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
            eq(CohortJobs.ReconcileListPayload(7L)),
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
}
