package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.platform.integration.cohort.persistence.Cohort
import net.blueshell.api.platform.integration.cohort.persistence.CohortKind
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortSubjectRepository
import net.blueshell.api.platform.integration.cohort.port.`in`.CohortTargeting
import net.blueshell.api.platform.integration.cohort.port.out.CohortPortRegistry
import net.blueshell.api.platform.integration.sync.application.ExternalIdMappingService
import net.blueshell.api.platform.integration.sync.application.ExternalIdMappingService.Companion.COHORT_AGGREGATE
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import net.blueshell.api.shared.job.CohortJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException

/**
 * Application implementation of [CohortTargeting]. External target
 * creation runs outside any DB transaction (the PR A reconcile pattern);
 * the local `Cohort` row and its `external_id_mapping` are persisted in a
 * short write transaction afterwards so a provider failure leaves no
 * orphan mapping.
 */
@Service
class CohortTargetingService(
    private val cohortRepo: CohortRepository,
    private val subjectRepo: CohortSubjectRepository,
    private val externalIds: ExternalIdMappingService,
    private val registry: CohortPortRegistry,
    private val jobs: TrackedJobDispatcher,
    transactionManager: PlatformTransactionManager,
) : CohortTargeting {

    private val writeTransaction = TransactionTemplate(transactionManager)

    // Suspends any active transaction (e.g. the one AbstractJsonJobHandler opens
    // around deleteTarget) so provider HTTP calls hold no DB connection —
    // ADR-006/ADR-023.
    private val outsideTransaction = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_NOT_SUPPORTED
    }

    override fun linkExisting(subjectId: Long, system: TargetSystem, externalId: String): CohortMappingRow =
        writeTransaction.execute {
            val subject = requireSubject(subjectId)
            requireNoExistingMapping(subjectId, system)
            val cohort = cohortRepo.save(newCohort(system, subject.label, folder = null, subjectId = subjectId))
            externalIds.upsert(COHORT_AGGREGATE, cohort.id!!, system.name, externalId)
            CohortMappingRow(cohort, externalId)
        }!!

    override fun create(subjectId: Long, system: TargetSystem, label: String, folderHint: String?): CohortMappingRow {
        // Validate before touching the provider so a duplicate/missing subject
        // never creates an external target.
        writeTransaction.execute {
            requireSubject(subjectId)
            requireNoExistingMapping(subjectId, system)
        }

        val externalId = outsideTransaction.execute { registry.require(system).createCohort(label, folderHint) }!!

        return writeTransaction.execute {
            val cohort = cohortRepo.save(newCohort(system, label, folder = folderHint, subjectId = subjectId))
            externalIds.upsert(COHORT_AGGREGATE, cohort.id!!, system.name, externalId)
            CohortMappingRow(cohort, externalId)
        }!!
    }

    override fun switchTarget(
        cohortId: Long,
        externalId: String,
        deletePrevious: Boolean,
        reconcileNow: Boolean,
    ): CohortMappingRow {
        val switched = writeTransaction.execute {
            val cohort = cohortRepo.findById(cohortId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Cohort $cohortId not found")
            }
            val system = TargetSystem.valueOf(cohort.system)
            val previousExternalId = externalIds.find(COHORT_AGGREGATE, cohortId, cohort.system)?.externalId
            externalIds.switchCohortTarget(cohortId, system, externalId)
            Switched(cohort, system, previousExternalId)
        }!!

        if (deletePrevious && switched.previousExternalId != null && switched.previousExternalId != externalId) {
            jobs.enqueue(
                CohortJobs.DeleteExternalTarget,
                CohortJobs.DeleteExternalTargetPayload(switched.system.name, switched.previousExternalId),
            )
        }
        if (reconcileNow) {
            jobs.enqueue(CohortJobs.ReconcileList, CohortJobs.ReconcileListPayload(cohortId))
        }
        return CohortMappingRow(switched.cohort, externalId)
    }

    override fun deleteTarget(system: TargetSystem, externalTargetId: String) {
        outsideTransaction.executeWithoutResult { registry.require(system).deleteCohort(externalTargetId) }
    }

    private fun requireSubject(subjectId: Long) =
        subjectRepo.findById(subjectId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Subject $subjectId not found")
        }

    private fun requireNoExistingMapping(subjectId: Long, system: TargetSystem) {
        if (cohortRepo.findBySubjectIdAndSystem(subjectId, system.name) != null) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Subject $subjectId already has a $system target",
            )
        }
    }

    private fun newCohort(system: TargetSystem, label: String, folder: String?, subjectId: Long) =
        Cohort(
            system = system.name,
            kind = kindFor(system),
            label = label,
            folder = folder,
            subjectId = subjectId,
        )

    private fun kindFor(system: TargetSystem): CohortKind = when (system) {
        TargetSystem.BREVO -> CohortKind.LIST
        TargetSystem.GOOGLE_CALENDAR ->
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$system has no cohort target kind")
    }

    private data class Switched(val cohort: Cohort, val system: TargetSystem, val previousExternalId: String?)
}
