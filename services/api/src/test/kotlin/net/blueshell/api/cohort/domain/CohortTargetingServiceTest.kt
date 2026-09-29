package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortKind
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortSubject
import net.blueshell.api.cohort.persistence.CohortSubjectRepository
import net.blueshell.api.cohort.persistence.CohortSubjectType
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
    private val cohortRepo = mock<CohortRepository>()
    private val subjectRepo = mock<CohortSubjectRepository>()
    private val targetIds = mock<CohortTargetIds>()
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
        service = CohortTargetingService(cohortRepo, subjectRepo, targetIds, strategies, jobs, txManager)
    }

    @Test
    fun `create does not touch the provider when the subject already maps the system`() {
        whenever(subjectRepo.findById(1L)).thenReturn(Optional.of(Entities.cohortSubject(id = 1L)))
        val linked = Entities.cohort()
        whenever(cohortRepo.findBySubjectIdAndSystem(1L, "BREVO")).thenReturn(linked)
        whenever(targetIds.find(linked)).thenReturn("list-1")

        val refused =
            assertThrows<ResponseStatusException> {
                service.create(1L, TargetSystem.BREVO, "Members", null)
            }
        assert(refused.reason!!.contains("switch it instead"))

        verify(strategy, never()).create(any(), any())
        verify(cohortRepo, never()).save(any())
    }

    @Test
    fun `create materialises the target and records the id`() {
        val saved = Entities.cohort(id = 42L)
        whenever(subjectRepo.findById(1L)).thenReturn(Optional.of(Entities.cohortSubject(id = 1L)))
        whenever(cohortRepo.findBySubjectIdAndSystem(1L, "BREVO")).thenReturn(null)
        whenever(strategy.create("Members", "Lists")).thenReturn(target("999", "Members", "Lists"))
        whenever(cohortRepo.save(any<Cohort>())).thenReturn(saved)

        val row = service.create(1L, TargetSystem.BREVO, "Members", "Lists")

        verify(strategy).create("Members", "Lists")
        verify(targetIds).record(saved, "999")
        assert(row.externalId == "999")
    }

    @Test
    fun `create fills a registered target that has no list yet, in its type's folder`() {
        val unlinked = Entities.cohort(id = 42L, system = "BREVO", label = "Paid 2026-2027", folder = "Contribution paid")
        whenever(subjectRepo.findById(1L)).thenReturn(Optional.of(Entities.cohortSubject(id = 1L)))
        whenever(cohortRepo.findBySubjectIdAndSystem(1L, "BREVO")).thenReturn(unlinked)
        whenever(cohortRepo.findById(42L)).thenReturn(Optional.of(unlinked))
        whenever(strategy.create("Paid 2026-2027", "Contribution paid")).thenReturn(target("999", "Paid 2026-2027", "Contribution paid"))

        val row = service.create(1L, TargetSystem.BREVO, "Paid 2026-2027", null)

        verify(cohortRepo, never()).save(any())
        verify(targetIds).record(unlinked, "999")
        assert(row.externalId == "999")
    }

    @Test
    fun `createFor claims the cohort, makes its target in its folder and reconciles it`() {
        val cohort = Entities.cohort(id = 7L, system = "BREVO", label = "Paid 2026-2027", folder = "Contribution paid")
        whenever(cohortRepo.findById(7L)).thenReturn(Optional.of(cohort))
        whenever(strategy.create("Paid 2026-2027", "Contribution paid")).thenReturn(target("55", "Paid 2026-2027", "Contribution paid"))

        val ref = service.createFor(7L)

        assert(ref.externalId == "55")
        assert(cohort.targetClaimedAt != null)
        verify(strategy, never()).catalog(anyOrNull())
        verify(targetIds).record(cohort, "55")
        verify(jobs).runAsync(
            eq(CohortJobs.ReconcileList),
            eq(CohortJobs.ReconcileListPayload(7L, JobTrigger.ANOTHER_JOB)),
            eq(JobTrigger.ANOTHER_JOB),
            anyOrNull(),
        )
    }

    @Test
    fun `a retried createFor finds the target its first run made instead of making a second`() {
        val cohort =
            Entities.cohort(id = 7L, system = "BREVO", label = "Paid 2026-2027", folder = "Contribution paid").apply {
                targetClaimedAt = java.time.Instant.parse("2026-09-29T10:00:00Z")
            }
        whenever(cohortRepo.findById(7L)).thenReturn(Optional.of(cohort))
        whenever(strategy.catalog("Paid 2026-2027")).thenReturn(
            listOf(target("54", "Paid 2026-2027", "Members"), target("55", "Paid 2026-2027", "Contribution paid")),
        )

        val ref = service.createFor(7L)

        assert(ref.externalId == "55")
        verify(strategy, never()).create(any(), anyOrNull())
        verify(targetIds).record(cohort, "55")
    }

    @Test
    fun `createFor is a no-op when the target already exists`() {
        val cohort = Entities.cohort(id = 7L, system = "BREVO", label = "Members")
        whenever(cohortRepo.findById(7L)).thenReturn(Optional.of(cohort))
        whenever(targetIds.find(cohort)).thenReturn("existing")

        val ref = service.createFor(7L)

        assert(ref.externalId == "existing")
        verify(strategy, never()).create(any(), anyOrNull())
        verify(targetIds, never()).record(any(), any())
    }

    @Test
    fun `createFor fails terminally for a cohort that is gone`() {
        whenever(cohortRepo.findById(7L)).thenReturn(Optional.empty())

        assertThrows<NonRetryableJobException> { service.createFor(7L) }
    }

    @Test
    fun `createMissing queues one create-target job per cohort without a target`() {
        whenever(cohortRepo.findAllBySubjectIdIsNotNullAndExternalIdIsNull()).thenReturn(
            listOf(Entities.cohort(id = 3L), Entities.cohort(id = 4L)),
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
        val subject = mock<net.blueshell.api.cohort.persistence.CohortSubject>()
        val cohort =
            Entities.cohort(id = 7L, externalId = null)
        whenever(subjectRepo.findById(1L)).thenReturn(Optional.of(subject))
        whenever(cohortRepo.findBySubjectIdAndSystem(1L, "BREVO")).thenReturn(cohort)

        val row = service.linkExisting(1L, TargetSystem.BREVO, "list-123")

        verify(strategy).resolve("list-123")
        verify(cohortRepo, never()).save(any())
        verify(targetIds).record(cohort, "list-123")
        assert(row.cohort == cohort)
        assert(row.externalId == "list-123")
    }

    @Test
    fun `linkExisting allows ids that are not present in the catalog`() {
        val subject = CohortSubject(CohortSubjectType.NEWSLETTER_SUBSCRIBERS, "Members")
        val saved = Entities.cohort(id = 7L)
        whenever(subjectRepo.findById(1L)).thenReturn(Optional.of(subject))
        whenever(cohortRepo.findBySubjectIdAndSystem(1L, "BREVO")).thenReturn(null)
        whenever(cohortRepo.save(any<Cohort>())).thenReturn(saved)
        whenever(strategy.resolve("missing-list")).thenReturn(null)

        service.linkExisting(1L, TargetSystem.BREVO, "missing-list")

        verify(strategy).resolve("missing-list")
        verify(targetIds).record(saved, "missing-list")
    }

    @Test
    fun `switch enqueues delete-previous and reconcile when asked`() {
        val cohort =
            Entities.cohort(system = "BREVO", subjectId = 1L)
        whenever(cohortRepo.findById(7L)).thenReturn(Optional.of(cohort))
        whenever(targetIds.find(cohort)).thenReturn("old-list")

        service.switchTarget(1L, 7L, "new-list", deletePrevious = true, reconcileNow = true)

        verify(strategy).resolve("new-list")
        verify(targetIds).record(cohort, "new-list")
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
        val cohort =
            Entities.cohort(system = "BREVO", subjectId = 1L)
        whenever(cohortRepo.findById(7L)).thenReturn(Optional.of(cohort))
        whenever(targetIds.find(cohort)).thenReturn(null)

        service.switchTarget(1L, 7L, "new-list", deletePrevious = true, reconcileNow = false)

        verify(jobs, never()).runAsync(eq(CohortJobs.DeleteExternalTarget), any(), any(), anyOrNull())
        verify(jobs, never()).runAsync(eq(CohortJobs.ReconcileList), any(), any(), anyOrNull())
    }

    @Test
    fun `switch rejects a cohort that is not a target of the path subject`() {
        val cohort = Entities.cohort(subjectId = 99L)
        whenever(cohortRepo.findById(7L)).thenReturn(Optional.of(cohort))

        assertThrows<ResponseStatusException> {
            service.switchTarget(1L, 7L, "new-list", deletePrevious = false, reconcileNow = false)
        }

        verify(targetIds, never()).record(any(), any())
    }

    @Test
    fun `deleteTarget calls the provider`() {
        service.deleteTarget(TargetSystem.BREVO, "stale-list")

        verify(strategy).delete(target("stale-list", "stale-list", null))
    }

    private fun target(
        id: String,
        label: String,
        folder: String?,
    ) = ExternalTarget(TargetSystem.BREVO, id, CohortKind.LIST, label, folder)

    private companion object {
        val brevoDescriptor =
            TargetDescriptor(
                system = TargetSystem.BREVO,
                kind = CohortKind.LIST,
            )
    }
}
