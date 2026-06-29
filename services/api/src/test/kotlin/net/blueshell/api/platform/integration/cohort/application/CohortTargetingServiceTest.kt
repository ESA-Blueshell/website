package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.platform.integration.cohort.persistence.Cohort
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortSubjectRepository
import net.blueshell.api.platform.integration.cohort.port.out.CohortPort
import net.blueshell.api.platform.integration.cohort.port.out.CohortPortRegistry
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.job.CohortJobs
import net.blueshell.api.shared.job.NonRetryableJobException
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.web.server.ResponseStatusException
import java.util.Optional

/**
 * Unit test for [CohortTargetingService]. The port edges (CohortPort,
 * CohortTargetIds, job dispatcher) are the seams — no Spring context.
 * A no-op transaction manager runs the TransactionTemplate callbacks inline.
 */
class CohortTargetingServiceTest {

    private val cohortRepo = mock<CohortRepository>()
    private val subjectRepo = mock<CohortSubjectRepository>()
    private val targetIds = mock<CohortTargetIds>()
    private val registry = mock<CohortPortRegistry>()
    private val jobs = mock<TrackedJobDispatcher>()
    private val port = mock<CohortPort>()

    private val txManager = object : PlatformTransactionManager {
        override fun getTransaction(definition: TransactionDefinition?): TransactionStatus = SimpleTransactionStatus()
        override fun commit(status: TransactionStatus) {}
        override fun rollback(status: TransactionStatus) {}
    }

    private val service = CohortTargetingService(cohortRepo, subjectRepo, targetIds, registry, jobs, txManager)

    @Test
    fun `create does not touch the provider when the subject already maps the system`() {
        whenever(subjectRepo.findById(1L)).thenReturn(Optional.of(mock()))
        whenever(cohortRepo.findBySubjectIdAndSystem(1L, "BREVO")).thenReturn(mock<Cohort>())

        assertThrows<ResponseStatusException> {
            service.create(1L, TargetSystem.BREVO, "Members", null)
        }

        verifyNoInteractions(registry)
        verify(cohortRepo, never()).save(any())
    }

    @Test
    fun `create materialises the target and records the id`() {
        val saved = mock<Cohort> { on { id } doReturn 42L }
        whenever(subjectRepo.findById(1L)).thenReturn(Optional.of(mock()))
        whenever(cohortRepo.findBySubjectIdAndSystem(1L, "BREVO")).thenReturn(null)
        whenever(registry.require(TargetSystem.BREVO)).thenReturn(port)
        whenever(port.createCohort("Members", "Lists")).thenReturn("999")
        whenever(cohortRepo.save(any<Cohort>())).thenReturn(saved)

        val row = service.create(1L, TargetSystem.BREVO, "Members", "Lists")

        verify(port).createCohort("Members", "Lists")
        verify(targetIds).record(saved, "999")
        assert(row.externalId == "999")
    }

    @Test
    fun `materialize without an existing target fails terminally and never creates a provider target`() {
        val cohort = mock<Cohort> {
            on { id } doReturn 7L
            on { system } doReturn "BREVO"
            on { label } doReturn "Members"
            on { folder } doReturn "Committees"
        }
        whenever(cohortRepo.findById(7L)).thenReturn(Optional.of(cohort))
        whenever(targetIds.find(cohort)).thenReturn(null)
        whenever(registry.require(TargetSystem.BREVO)).thenReturn(port)

        assertThrows<NonRetryableJobException> {
            service.materialize(7L)
        }

        verify(port, never()).createCohort(any(), any())
        verify(targetIds, never()).record(any(), any())
    }

    @Test
    fun `materialize is a no-op when the target already exists`() {
        val cohort = mock<Cohort> { on { id } doReturn 7L; on { system } doReturn "BREVO"; on { label } doReturn "Members" }
        whenever(cohortRepo.findById(7L)).thenReturn(Optional.of(cohort))
        whenever(targetIds.find(cohort)).thenReturn("existing")

        val ref = service.materialize(7L)

        assert(ref.externalId == "existing")
        verifyNoInteractions(registry)
        verify(targetIds, never()).record(any(), any())
    }

    @Test
    fun `linkExisting fills an existing unbound mapping`() {
        val subject = mock<net.blueshell.api.platform.integration.cohort.persistence.CohortSubject>()
        val cohort = mock<Cohort> {
            on { id } doReturn 7L
            on { externalId } doReturn null
        }
        whenever(subjectRepo.findById(1L)).thenReturn(Optional.of(subject))
        whenever(cohortRepo.findBySubjectIdAndSystem(1L, "BREVO")).thenReturn(cohort)

        val row = service.linkExisting(1L, TargetSystem.BREVO, "list-123")

        verify(cohortRepo, never()).save(any())
        verify(targetIds).record(cohort, "list-123")
        assert(row.cohort == cohort)
        assert(row.externalId == "list-123")
    }

    @Test
    fun `switch enqueues delete-previous and reconcile when asked`() {
        val cohort = mock<Cohort> { on { system } doReturn "BREVO"; on { subjectId } doReturn 1L }
        whenever(cohortRepo.findById(7L)).thenReturn(Optional.of(cohort))
        whenever(targetIds.find(cohort)).thenReturn("old-list")

        service.switchTarget(1L, 7L, "new-list", deletePrevious = true, reconcileNow = true)

        verify(targetIds).record(cohort, "new-list")
        verify(jobs).enqueue(
            eq(CohortJobs.DeleteExternalTarget),
            eq(CohortJobs.DeleteExternalTargetPayload("BREVO", "old-list")),
        )
        verify(jobs).enqueue(
            eq(CohortJobs.ReconcileList),
            eq(CohortJobs.ReconcileListPayload(7L)),
        )
    }

    @Test
    fun `switch does not enqueue a delete when there is no previous target`() {
        val cohort = mock<Cohort> { on { system } doReturn "BREVO"; on { subjectId } doReturn 1L }
        whenever(cohortRepo.findById(7L)).thenReturn(Optional.of(cohort))
        whenever(targetIds.find(cohort)).thenReturn(null)

        service.switchTarget(1L, 7L, "new-list", deletePrevious = true, reconcileNow = false)

        verify(jobs, never()).enqueue(eq(CohortJobs.DeleteExternalTarget), any())
        verify(jobs, never()).enqueue(eq(CohortJobs.ReconcileList), any())
    }

    @Test
    fun `switch rejects a cohort that is not a target of the path subject`() {
        val cohort = mock<Cohort> { on { subjectId } doReturn 99L }
        whenever(cohortRepo.findById(7L)).thenReturn(Optional.of(cohort))

        assertThrows<ResponseStatusException> {
            service.switchTarget(1L, 7L, "new-list", deletePrevious = false, reconcileNow = false)
        }

        verify(targetIds, never()).record(any(), any())
    }

    @Test
    fun `deleteTarget calls the provider`() {
        whenever(registry.require(TargetSystem.BREVO)).thenReturn(port)

        service.deleteTarget(TargetSystem.BREVO, "stale-list")

        verify(port).deleteCohort("stale-list")
    }
}
